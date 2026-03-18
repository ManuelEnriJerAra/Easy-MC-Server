package vista;

import controlador.GestorServidores;
import controlador.IPPublica;
import modelo.Server;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;

public class PanelTotalServidor extends JPanel {
    PanelTotalServidor(GestorServidores gestorServidores){
        this.setLayout(new BorderLayout(10, 0));
        this.setOpaque(true);
        this.setBackground(AppTheme.getPanelBackground());
        this.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Previsualización e información a la izquierda
        JPanel panelInfo = new JPanel(new BorderLayout());
        panelInfo.setOpaque(false);
        PanelPrevisualizacion panelPrevisualizacion = new PanelPrevisualizacion(gestorServidores);
        JPanel panelIPyTipo = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        panelIPyTipo.setOpaque(false);

        Server server = gestorServidores.getServidorSeleccionado();
        String tipo = (server == null || server.getTipo() == null || server.getTipo().isBlank())
                ? "DESCONOCIDO"
                : server.getTipo();
        JLabel tipoLabel = new JLabel(tipo);
        JLabel ipServidor = new JLabel("IP: (cargando...)");
        ipServidor.putClientProperty("fullText", "IP: (cargando...)");
        ipServidor.setFont(ipServidor.getFont().deriveFont(Font.BOLD, 16f));
        ipServidor.setForeground(AppTheme.getLinkForeground());
        ipServidor.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        Runnable actualizarTexto = () -> {
            String ip = (String) ipServidor.getClientProperty("ipPublica");
            if(ip == null || ip.isBlank()){
                ipServidor.setText("IP: (sin conexion)");
                ipServidor.putClientProperty("fullText", "IP: (sin conexion)");
                ipServidor.putClientProperty("copyText", null);
                return;
            }
            int puerto = (server == null || server.getServerConfig() == null) ? 0 : server.getServerConfig().getPuerto();
            String texto = puerto > 0 ? (ip + ":" + puerto) : (ip + ":(sin puerto)");
            ipServidor.putClientProperty("fullText", texto);
            ipServidor.putClientProperty("copyText", texto);

            // Ellipsize según espacio disponible
            SwingUtilities.invokeLater(() -> {
                int maxWidth = Math.max(0, panelIPyTipo.getWidth() - tipoLabel.getPreferredSize().width - 20);
                aplicarEllipsisLabel(ipServidor, maxWidth);
            });
        };

        IPPublica.getAsync(ip -> {
            ipServidor.putClientProperty("ipPublica", ip);
            actualizarTexto.run();
        });

        PropertyChangeListener listenerEstado = null;
        if(server != null){
            listenerEstado = evt -> {
                if(!"estadoServidor".equals(evt.getPropertyName())) return;
                Object v = evt.getNewValue();
                if(!(v instanceof Server s)) return;
                if(!s.getId().equals(server.getId())) return;
                SwingUtilities.invokeLater(actualizarTexto);
            };
            gestorServidores.addPropertyChangeListener("estadoServidor", listenerEstado);
        }

        ipServidor.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Object copyObj = ipServidor.getClientProperty("copyText");
                if(!(copyObj instanceof String copyText) || copyText.isBlank()) return;
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(copyText), null);
                JOptionPane.showMessageDialog(
                        SwingUtilities.getWindowAncestor(PanelTotalServidor.this),
                        "Copiado al portapapeles: " + copyText,
                        "Copiado",
                        JOptionPane.INFORMATION_MESSAGE
                );
            }
        });


        panelIPyTipo.add(ipServidor);
        panelIPyTipo.add(tipoLabel);

        panelIPyTipo.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int maxWidth = Math.max(0, panelIPyTipo.getWidth() - tipoLabel.getPreferredSize().width - 20);
                aplicarEllipsisLabel(ipServidor, maxWidth);
            }
        });

        panelInfo.add(panelPrevisualizacion, BorderLayout.NORTH);
        panelInfo.add(panelIPyTipo, BorderLayout.SOUTH);
        // Va en CENTER para que se adapte/recorte y no invada el espacio del botón
        this.add(panelInfo, BorderLayout.CENTER);

        // Controles a la derecha
        PanelControlServidor panelControlServidor = new PanelControlServidor(gestorServidores);
        // En BorderLayout.EAST el panel se estira en altura; queremos que los botones ocupen todo el alto disponible.
        JPanel controlWrap = new JPanel(new BorderLayout());
        controlWrap.setOpaque(false);
        controlWrap.add(panelControlServidor, BorderLayout.CENTER);
        this.add(controlWrap, BorderLayout.EAST);

        PropertyChangeListener finalListenerEstado = listenerEstado;
        this.addHierarchyListener(e -> {
            if(!isDisplayable() && finalListenerEstado != null){
                gestorServidores.removePropertyChangeListener("estadoServidor", finalListenerEstado);
            }
        });

    }

    @Override
    public void updateUI(){
        super.updateUI();
        this.setBackground(AppTheme.getPanelBackground());
        this.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    }

    private void aplicarEllipsisLabel(JLabel label, int maxWidth){
        if(label == null) return;
        Object fullObj = label.getClientProperty("fullText");
        String full = (fullObj instanceof String s) ? s : label.getText();
        if(full == null) full = "";
        label.setToolTipText(full);

        FontMetrics fm = label.getFontMetrics(label.getFont());
        label.setText(ellipsizePx(full, fm, maxWidth));
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
}
