/*
 * Fichero: PanelServidores.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 * Fecha: 17/01/2026
 *
 * Descripción:
 * Este panel extiende de JScrollPane y es el encargado de mostrar al usuario la lista de servidores guardados.
 * Es interactuable y manda la información del servidor seleccionado a ---------------------------------------
 * */

package Vista;

import Controlador.GestorServidores;
import Controlador.Utilidades;
import Modelo.Server;
import com.formdev.flatlaf.extras.components.FlatScrollPane;
import com.formdev.flatlaf.ui.*;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PanelServidores extends FlatScrollPane {
    int arc = UIManager.getInt("Component.arc");

    Color acento = UIManager.getColor("Component.focusColor");
    Color base = UIManager.getColor("Component.borderColor");
    Color bgNormal = UIManager.getColor("Panel.background");
    Color bgHover = oscurecer(bgNormal, 0.06f);
    Color bgPresionado = oscurecer(bgNormal, 0.12f);
    Color bgSelected = tintar(acento, bgNormal, 0.18f);

    Insets insets = new Insets(3, 3, 3, 3);
    // Bordes
    private final FlatLineBorder bordeRedondo = new FlatLineBorder(insets, base, 1f, arc);
    private final FlatLineBorder bordeHover = new FlatLineBorder(insets, acento, 1f, arc);
    private final FlatLineBorder bordeSeleccionado = new FlatLineBorder(insets, acento, 3f, arc);

    JPanel filaSeleccionada;

    public interface ServidorSeleccionadoListener{
        void servidorSeleccionado(Server server);
    }
    private ServidorSeleccionadoListener listener;

    public void setServidorSeleccionadoListener(ServidorSeleccionadoListener listener){
        this.listener = listener;
    }

    GestorServidores gestorServidores;

    // Constructor de PanelServidores
    PanelServidores(GestorServidores gestorServidores){
        this.gestorServidores = gestorServidores;
        this.setBorder(null);
        gestorServidores.addPropertyChangeListener(evt -> {
            // esto se ejecuta si nos llega un mensaje del gestor de servidores con nombre de evento: "listaServidores"
           if("listaServidores".equals(evt.getPropertyName())){
               // cambiamos la lista de servidores
               List<Server> servidores = (List<Server>) evt.getNewValue();
               // actualizamos el panel
               recargarPanel(servidores, gestorServidores);
           }
        });

        JPanel panelContenedor = new JPanel(); // panel que engloba todas las filas
        panelContenedor.setLayout(new BoxLayout(panelContenedor, BoxLayout.Y_AXIS));
        List<Server> servidores = gestorServidores.getListaServidores(); // hacemos una lista de ServerConfig donde se guardarán los servidores
        List<Server> servidoresEliminar = new ArrayList<>();
        for (Server server : servidores) { // la recorremos y para cada una de ellas
            // Compruebo la versión lo primero para saber si el servidor es correcto
            if(server.getVersion()!=null){
                JPanel fila = crearFila(server, server.getVersion());

                panelContenedor.add(fila);
            }
            // en el caso de que el servidor introducido no sea correcto
            else{
                servidoresEliminar.add(server);
            }
        }

        JPanel panelDeError = new JPanel(new BorderLayout());
        JTextArea errorText = new JTextArea("Se eliminarán los siguientes servidores de la lista de servidores guardados" +
                ", si la ubicación ha cambiado puedes volver a importarlos");
        errorText.setEditable(false);
        errorText.setLineWrap(true);
        errorText.setWrapStyleWord(true);
        errorText.setOpaque(true);
        JScrollPane scrollPaneEliminar = new JScrollPane();
        panelDeError.add(errorText, BorderLayout.NORTH);
        panelDeError.add(scrollPaneEliminar, BorderLayout.CENTER);
        JList<Server> lista = new JList<>(servidoresEliminar.toArray(new Server[0]));
        scrollPaneEliminar.setViewportView(lista);
        JOptionPane.showMessageDialog(this, panelDeError, "Servidores incorrectos", JOptionPane.WARNING_MESSAGE);
        gestorServidores.quitarListaServidoresJSON(servidores);
        this.setViewportView(panelContenedor);
    }

    private void recargarPanel(List<Server> servidores, GestorServidores gestorServidores){
        JPanel panelContenedor = new JPanel();
        panelContenedor.setLayout(new BoxLayout(panelContenedor, BoxLayout.Y_AXIS));

        List<Server> servidoresEliminar = new ArrayList<>();

        for(Server servidor : servidores){
            if(servidor.getVersion() != null){
                JPanel fila = crearFila(servidor, servidor.getVersion());
                panelContenedor.add(fila);
            } else {
                servidoresEliminar.add(servidor);
            }
        }

        // Mostrar errores de servidores incorrectos
        if(!servidoresEliminar.isEmpty()){
            mostrarErrores(servidoresEliminar);
            gestorServidores.quitarListaServidoresJSON(servidoresEliminar);
        }

        // Actualizamos el JScrollPane
        this.setViewportView(panelContenedor);
        this.revalidate();
        this.repaint();
    }

    private JPanel crearFila(Server servidor, String version){
        final int ALTURA_FILA = 90;
        final int ANCHO_FILA = 250;

        JPanel fila = new JPanel(new BorderLayout(10,0));
        fila.setBorder(bordeRedondo);
        fila.setBackground(bgNormal);
        fila.setOpaque(true);

        fila.setPreferredSize(new Dimension(ANCHO_FILA, ALTURA_FILA));
        fila.setMinimumSize(new Dimension(ANCHO_FILA, ALTURA_FILA));
        fila.setMaximumSize(new Dimension(ANCHO_FILA, ALTURA_FILA));

        // Imagen del servidor a la izquierda, escalada y redondeada
        ImageIcon icono = servidor.getServerIconOrUseDefault();
        ImagenRedondaLabel iconoRedondo = new ImagenRedondaLabel(icono, 10, 64, 64);

        JPanel panelIcono = new JPanel(new GridBagLayout());
        panelIcono.setOpaque(false);
        panelIcono.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 0));
        panelIcono.add(iconoRedondo);

        panelIcono.setPreferredSize(new Dimension(64 + 8 + 8, ALTURA_FILA));
        panelIcono.setMinimumSize(new Dimension(64 + 8 + 8, ALTURA_FILA));

        fila.add(panelIcono, BorderLayout.WEST);

        // Panel de texto, nombre arriba, MOTD centro, versión abajo
        JPanel panelDerecho = new JPanel(new BorderLayout(0,0));
        panelDerecho.setOpaque(false);
        panelDerecho.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 10));

        // Info arriba
        JPanel infoPanel = new JPanel();
        infoPanel.setOpaque(false);
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));

        JLabel nombreLabel = new JLabel(ellipsize(servidor.getDisplayName(), 28));
        nombreLabel.setFont(nombreLabel.getFont().deriveFont(Font.BOLD, 16f));
        nombreLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel motdLabel = new JLabel(ellipsize(Utilidades.leerMotdDesdeProperties(Path.of(servidor.getServerDir())),34));
        motdLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel versionLabel = new JLabel(ellipsize(version, 34));
        versionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        infoPanel.add(nombreLabel);
        infoPanel.add(motdLabel);
        infoPanel.add(versionLabel);

        panelDerecho.add(infoPanel, BorderLayout.NORTH);

        // Indicador de estado (Abajo)
        JLabel estado = new JLabel();
        estado.setFont(estado.getFont().deriveFont(Font.BOLD, 14f)); // que esté bien grande
        boolean vivo = servidor.getServerProcess() != null && servidor.getServerProcess().isAlive();
        estado.setForeground(vivo ? Color.green : Color.red);
        estado.setText(vivo ? "● Activo" : "● Inactivo");
        estado.setAlignmentX(Component.LEFT_ALIGNMENT);

        panelDerecho.add(estado, BorderLayout.SOUTH);
        fila.add(panelDerecho, BorderLayout.CENTER);

        // Interacción y propiedades
        fila.putClientProperty("server", servidor);
        fila.putClientProperty("estadoLabel", estado);
        fila.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        fila.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e){
                fila.setBackground(bgPresionado);
                fila.repaint();

            }

            @Override
            public void mouseReleased(MouseEvent e){
                // nos aseguramos de que suelte dentro de la caja
                boolean dentro = fila.contains(e.getPoint());
                if(!dentro){
                    if(fila == filaSeleccionada) fila.setBackground(bgSelected);
                    else fila.setBackground(bgNormal);
                    fila.repaint();
                    return;
                }

                Server serverSeleccionado = (Server) fila.getClientProperty("server");
                marcarFilaSeleccionada(fila);

                if(listener != null){
                    listener.servidorSeleccionado(serverSeleccionado);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e){
                if(fila != filaSeleccionada){
                    fila.setBorder(bordeHover);
                    fila.setBackground(bgHover);
                    fila.repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e){
                if (fila != filaSeleccionada){
                    fila.setBorder(bordeRedondo);
                    fila.setBackground(bgNormal);
                    fila.repaint();
                }
            }
        });
        fila.setBorder(bordeRedondo);
        return fila;
    }

    public void actualizarEstados(){
        Component vista = getViewport().getView();
        if(!(vista instanceof JPanel panelContenedor)) return;

        for(Component componente : panelContenedor.getComponents()){
            if(!(componente instanceof JPanel fila)) continue;

            Object serverObj = fila.getClientProperty("server");
            Object estadoObj = fila.getClientProperty("estadoLabel");

            if(!(serverObj instanceof Server server)) continue;
            if(!(estadoObj instanceof JLabel estado)) continue;

            boolean vivo = server.getServerProcess() != null && server.getServerProcess().isAlive();
            estado.setForeground(vivo ? Color.green : Color.red);
        }

    }

    // Este método es el encargado de mostrar los errores que han ocurrido durante la inicialización del panel
    private void mostrarErrores(List<Server> servidoresEliminar){
        JPanel panelDeError = new JPanel(new BorderLayout());
        JTextArea errorText = new JTextArea(
                "Se eliminarán los siguientes servidores de la lista de servidores guardados, " +
                        "si la ubicación ha cambiado puedes volver a importarlos:"
        );
        errorText.setEditable(false);
        errorText.setLineWrap(true);
        errorText.setWrapStyleWord(true);
        errorText.setOpaque(true);

        JScrollPane scrollPaneEliminar = new JScrollPane(
                new JList<>(servidoresEliminar.toArray(new Server[0]))
        );

        panelDeError.add(errorText, BorderLayout.NORTH);
        panelDeError.add(scrollPaneEliminar, BorderLayout.CENTER);

        JOptionPane.showMessageDialog(this, panelDeError, "Servidores incorrectos", JOptionPane.WARNING_MESSAGE);
    }


    private void marcarFilaSeleccionada(JPanel fila){
        if(filaSeleccionada != null){
            filaSeleccionada.setBorder(bordeRedondo);
            filaSeleccionada.setBackground(bgNormal);
            filaSeleccionada.repaint();
        }

        filaSeleccionada = fila;
        fila.setBackground(bgSelected);
        fila.setBorder(bordeSeleccionado);

        fila.repaint();
    }

    // si el texto no entra añade "..."
    private String ellipsize(String s, int maxChars){
        if(s == null) return "";
        s = s.strip();
        if(s.length() < maxChars) return s;
        return s.substring(0, Math.max(0, maxChars-1)) + "...";
    }

    private Color oscurecer(Color c, float amount) {
        amount = Math.max(0f, Math.min(1f, amount));
        int r = (int) (c.getRed()   * (1f - amount));
        int g = (int) (c.getGreen() * (1f - amount));
        int b = (int) (c.getBlue()  * (1f - amount));
        return new Color(r, g, b);
    }

    private Color tintar(Color accent, Color base, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (base.getRed()   + (accent.getRed()   - base.getRed())   * t);
        int g = (int) (base.getGreen() + (accent.getGreen() - base.getGreen()) * t);
        int b = (int) (base.getBlue()  + (accent.getBlue()  - base.getBlue())  * t);
        return new Color(r, g, b);
    }

}
