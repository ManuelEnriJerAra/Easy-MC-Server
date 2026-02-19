/*
 * Fichero: PanelControlServidor.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 * Fecha: 17/01/2026
 *
 * Descripción:
 * ----------------------
 * */

package Vista;

import Controlador.GestorServidores;
import Modelo.Server;
import com.formdev.flatlaf.extras.components.FlatButton;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.HexFormat;

public class PanelControlServidor extends JPanel {
    private static final int DEFAULT_SIDE_PX = 96;
    private static final int RIGHT_PADDING_PX = 10;
    private final PropertyChangeListener listenerEstadoServidor;
    private JPanel panelBotones;
    private JButton btnIniciarServidor;
    private JButton btnPararServidor;
    private JButton btnReiniciarServidor;
    private FlatButton btnForzarCierreServidor;
    private JPanel panelBotonesIniciado;

    PanelControlServidor(GestorServidores gestorServidores) {
        Color colorVerde = new Color(129, 247, 135);
        Color colorRojo = new Color(255, 87, 87);
        Color colorAmarillo = new Color(255, 214, 99);

        Server server = gestorServidores.getServidorSeleccionado();
        this.setLayout(new BorderLayout());
        this.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, RIGHT_PADDING_PX));
        panelBotones = new JPanel(new BorderLayout());
        btnIniciarServidor = new JButton("Iniciar Servidor");
        btnIniciarServidor.putClientProperty("fullText", "Iniciar Servidor");
        btnIniciarServidor.setBackground(colorVerde);
        btnPararServidor = new JButton("Parar Servidor");
        btnPararServidor.putClientProperty("fullText", "Parar Servidor");
        btnPararServidor.setBackground(colorRojo);
        btnReiniciarServidor = new JButton("Reiniciar Servidor");
        btnReiniciarServidor.putClientProperty("fullText", "Reiniciar Servidor");
        btnReiniciarServidor.setBackground(colorAmarillo);
        btnForzarCierreServidor = new FlatButton();
        btnForzarCierreServidor.setText("Forzar Cierre Servidor");
        btnForzarCierreServidor.setOutline(true);
        btnForzarCierreServidor.setBackground(Color.RED);

        panelBotonesIniciado = new JPanel(new GridBagLayout());
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

        btnIniciarServidor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    gestorServidores.iniciarServidor(server);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        btnPararServidor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                gestorServidores.safePararServidor(server);
            }
        });
        btnReiniciarServidor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                server.setRestartPending(true);
                gestorServidores.safePararServidor(server);
            }
        });
        btnForzarCierreServidor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                gestorServidores.forzarPararServidor(server);
            }
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

        // Sincroniza el estado inicial (si el servidor ya estaba iniciado, no llega ningÃºn evento)
        actualizarBotonesSegunEstado(server);

        // Ellipsize del texto cuando el panel sea pequeÃ±o
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

        // Recalcular ellipsize tras cambiar el layout
        SwingUtilities.invokeLater(() -> {
            ellipsizeButtonText(btnIniciarServidor);
            ellipsizeButtonText(btnPararServidor);
            ellipsizeButtonText(btnReiniciarServidor);
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
    public Dimension getPreferredSize(){
        Dimension base = super.getPreferredSize();
        int h = getHeight();
        int side = h > 0 ? h : (base == null ? 0 : base.height);
        if(side <= 0) side = DEFAULT_SIDE_PX;
        return new Dimension(side + RIGHT_PADDING_PX, side);
    }

    @Override
    public Dimension getMinimumSize(){
        return new Dimension(DEFAULT_SIDE_PX + RIGHT_PADDING_PX, DEFAULT_SIDE_PX);
    }

}
