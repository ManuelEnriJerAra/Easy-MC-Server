/*
 * Fichero: PanelControlServidor.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 * Fecha: 17/01/2026
 *
 * Descripción:
 * ----------------------
 * */

package vista;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.RepaintManager;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.extras.components.FlatButton;

import controlador.GestorServidores;
import modelo.Server;

public class PanelControlServidor extends JPanel {
    private static final int DEFAULT_SIDE_PX_X = 146;
    private static final int DEFAULT_SIDE_PX_Y = 96;
    private static final int RIGHT_PADDING_PX = 10;
    private final PropertyChangeListener listenerEstadoServidor;
    private JPanel panelBotones;
    private JButton btnIniciarServidor;
    private JButton btnPararServidor;
    private JButton btnReiniciarServidor;
    private FlatButton btnForzarCierreServidor;
    private JPanel panelBotonesIniciado;

    PanelControlServidor(GestorServidores gestorServidores) {
        Color colorVerde = AppTheme.getSuccessColor();
        Color colorRojo = AppTheme.getDangerColor();
        Color colorAmarillo = AppTheme.getWarningColor();

        Server server = gestorServidores.getServidorSeleccionado();
        this.setLayout(new BorderLayout());
        this.setOpaque(false);
        this.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, RIGHT_PADDING_PX));
        panelBotones = new JPanel(new BorderLayout());
        panelBotones.setOpaque(false);
        btnIniciarServidor = new FlatButton();
        btnIniciarServidor.setText("Iniciar");
        btnIniciarServidor.putClientProperty("fullText", "Iniciar");
        btnIniciarServidor.setBackground(colorVerde);
        btnIniciarServidor.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnPararServidor = new FlatButton();
        btnPararServidor.setText("Parar");
        btnPararServidor.putClientProperty("fullText", "Parar");
        btnPararServidor.setBackground(colorRojo);
        btnPararServidor.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnReiniciarServidor = new FlatButton();
        btnReiniciarServidor.setText("Reiniciar");
        btnReiniciarServidor.putClientProperty("fullText", "Reiniciar");
        btnReiniciarServidor.setBackground(colorAmarillo);
        btnReiniciarServidor.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnForzarCierreServidor = new FlatButton();
        btnForzarCierreServidor.setText("Forzar Cierre Servidor");
        btnForzarCierreServidor.setOutline(true);
        btnForzarCierreServidor.setBackground(AppTheme.getDestructiveColor());
        btnForzarCierreServidor.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        FlatSVGIcon iconPlay = new FlatSVGIcon("easymcicons/play.svg", 32, 32);
        FlatSVGIcon iconPause = new FlatSVGIcon("easymcicons/stop.svg", 32, 32);
        FlatSVGIcon iconReset = new FlatSVGIcon("easymcicons/reset.svg", 32, 32);

        configurarBotonAccion(btnIniciarServidor, iconPlay);
        configurarBotonAccion(btnPararServidor, iconPause);
        configurarBotonAccion(btnReiniciarServidor, iconReset);

        panelBotonesIniciado = new JPanel(new GridBagLayout());
        panelBotonesIniciado.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        // Parar ocupa 3/4 del alto
        gbc.gridy = 0;
        gbc.weighty = 3.0;
        panelBotonesIniciado.add(btnPararServidor, gbc);
        // Reiniciar ocupa 1/4 del alto
        gbc.gridy = 1;
        gbc.weighty = 1.0;
        panelBotonesIniciado.add(btnReiniciarServidor, gbc);

        btnIniciarServidor.addActionListener((ActionEvent e) -> {
            try {
                gestorServidores.iniciarServidor(server);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
        btnPararServidor.addActionListener((ActionEvent e) -> {
            gestorServidores.safePararServidor(server);
        });
        btnReiniciarServidor.addActionListener((ActionEvent e) -> {
            server.setRestartPending(true);
            gestorServidores.safePararServidor(server);
        });
        btnForzarCierreServidor.addActionListener((ActionEvent e) -> {
            gestorServidores.forzarPararServidor(server);
        });

        this.listenerEstadoServidor = evt -> {
            if(!"estadoServidor".equals(evt.getPropertyName())) return;
            Server serverEvento = (Server) evt.getNewValue();
            if(serverEvento == null || !serverEvento.equals(server)) return;
            SwingUtilities.invokeLater(()->{
                actualizarBotonesSegunEstado(server);
            });
        };

        gestorServidores.addPropertyChangeListener("estadoServidor", listenerEstadoServidor);
        this.add(panelBotones, BorderLayout.CENTER);

        // Sincroniza el estado inicial (si el servidor ya estaba iniciado, no llega ningún evento)
        actualizarBotonesSegunEstado(server);

        // Ellipsize del texto cuando el panel sea pequeño
        panelBotones.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                SwingUtilities.invokeLater(() -> {
                    ellipsizeButtonText(btnIniciarServidor);
                    ellipsizeButtonText(btnPararServidor);
                    ellipsizeButtonText(btnReiniciarServidor);
                });
            }
        });

        // Evita fugas de listeners al cambiar de servidor/panel (solo cuando el componente deja de ser displayable)
        this.addHierarchyListener(e -> {
            if((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) == 0) return;
            if(!isDisplayable()){
                gestorServidores.removePropertyChangeListener("estadoServidor", listenerEstadoServidor);
            }
        });

        this.setVisible(true);
    }

    private void actualizarBotonesSegunEstado(Server server){
        panelBotones.removeAll();
        if(server.getServerProcess() != null && server.getServerProcess().isAlive()){
            // el servidor está vivo
            panelBotones.add(panelBotonesIniciado, BorderLayout.CENTER);
        }
        else{
            // el servidor no está vivo
            panelBotones.add(btnIniciarServidor, BorderLayout.CENTER);
        }
        panelBotones.revalidate();
        panelBotones.repaint();
        revalidate();
        repaint();

        // Recalcular ellipsize tras cambiar el layout y forzar un repintado completo,
        // porque los paneles transparentes/redondeados vecinos pueden dejar artefactos
        // hasta el siguiente repaint global de la ventana.
        SwingUtilities.invokeLater(() -> {
            ellipsizeButtonText(btnIniciarServidor);
            ellipsizeButtonText(btnPararServidor);
            ellipsizeButtonText(btnReiniciarServidor);

            JRootPane root = SwingUtilities.getRootPane(PanelControlServidor.this);
            if(root != null){
                RepaintManager.currentManager(root).markCompletelyDirty(root);
                root.revalidate();
                root.repaint();
            }
        });
    }

    private void ellipsizeButtonText(AbstractButton button){
        if(button == null) return;
        Object fullObj = button.getClientProperty("fullText");
        String fullText = (fullObj instanceof String s) ? s : button.getText();
        if(fullText == null) fullText = "";
        button.setToolTipText(fullText);

        Insets ins = button.getInsets();
        int available = Math.max(0, button.getWidth() - ins.left - ins.right - 12);
        FontMetrics fm = button.getFontMetrics(button.getFont());
        button.setText(ellipsizePx(fullText, fm, available));
    }

    private String ellipsizePx(String text, FontMetrics fm, int maxWidth){
        if(text == null) return "";
        if(fm == null) return text;
        if(maxWidth <= 0) return "...";
        if(fm.stringWidth(text) <= maxWidth) return text;

        String ell = "...";
        int ellW = fm.stringWidth(ell);
        int target = Math.max(0, maxWidth - ellW);

        int lo = 0;
        int hi = text.length();
        while(lo < hi){
            int mid = (lo + hi + 1) >>> 1;
            String sub = text.substring(0, mid);
            if(fm.stringWidth(sub) <= target) lo = mid; else hi = mid - 1;
        }
        return text.substring(0, lo) + ell;
    }

@Override
public Dimension getPreferredSize() {
    return new Dimension(DEFAULT_SIDE_PX_X + RIGHT_PADDING_PX, DEFAULT_SIDE_PX_Y);
}

@Override
public Dimension getMinimumSize() {
    return new Dimension(DEFAULT_SIDE_PX_X + RIGHT_PADDING_PX, DEFAULT_SIDE_PX_Y);
}

    private void configurarBotonAccion(AbstractButton button, FlatSVGIcon flatIcon) {
        if (button == null) {
            return;
        }
        button.setForeground(Color.WHITE);
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setHorizontalTextPosition(SwingConstants.RIGHT);
        button.setVerticalTextPosition(SwingConstants.CENTER);
        button.setIconTextGap(8);
        button.setFont(button.getFont().deriveFont(Font.BOLD, button.getFont().getSize2D() + 5f));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setIcon(flatIcon);
        flatIcon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> Color.WHITE));
    }

}
