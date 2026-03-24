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

package vista;

import controlador.GestorServidores;
import controlador.Utilidades;
import modelo.Server;
import com.formdev.flatlaf.extras.components.FlatScrollPane;
import com.formdev.flatlaf.ui.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PanelServidores extends FlatScrollPane {
    private static final Dimension MIN_SIZE = new Dimension(240, 240); // ancho justo para que no tape el estado "Inactivo"
    int arc;
    Color acento;
    Color base;
    Color bgLista;
    Color bgNormal;
    Color bgHover;
    Color bgPresionado;
    Color bgSelected;

    Insets insets = new Insets(3, 3, 3, 3);
    // Bordes
    private FlatLineBorder bordeRedondo;
    private FlatLineBorder bordeHover;
    private FlatLineBorder bordeSeleccionado;

    JPanel filaSeleccionada;
    private boolean uiReady;

    public interface ServidorSeleccionadoListener{
        void servidorSeleccionado(Server server);
    }

    public interface ServidorContextMenuListener{
        void abrirConfiguracion(Server server);
        void abrirMundos(Server server);
    }

    private ServidorSeleccionadoListener listener;
    private ServidorContextMenuListener contextMenuListener;

    public void setServidorSeleccionadoListener(ServidorSeleccionadoListener listener){
        this.listener = listener;
    }

    public void setServidorContextMenuListener(ServidorContextMenuListener contextMenuListener){
        this.contextMenuListener = contextMenuListener;
    }

    public void seleccionarServidor(Server server){
        seleccionarServidor(server, true);
    }

    public void mostrarSeleccionServidor(Server server){
        seleccionarServidor(server, false);
    }

    private void seleccionarServidor(Server server, boolean notificar){
        if(server == null) return;
        refrescarTema(true);

        Component vista = getViewport().getView();
        if(!(vista instanceof JPanel panelContenedor)) return;

        for(Component componente : panelContenedor.getComponents()){
            if(!(componente instanceof JPanel fila)) continue;

            Object serverObj = fila.getClientProperty("server");
            if(!(serverObj instanceof Server filaServer)) continue;

            boolean coincidePorId = Objects.equals(filaServer.getId(), server.getId());
            boolean coincidePorDir = filaServer.getId() == null
                    && server.getId() == null
                    && Objects.equals(filaServer.getServerDir(), server.getServerDir());

            if(!coincidePorId && !coincidePorDir) continue;

            marcarFilaSeleccionada(fila);
            fila.scrollRectToVisible(new Rectangle(0, 0, fila.getWidth(), fila.getHeight()));
            if(notificar && listener != null){
                listener.servidorSeleccionado(filaServer);
            }
            return;
        }
    }

    public void seleccionarPrimero(){
        refrescarTema(true);
        Component vista = getViewport().getView();
        if(!(vista instanceof JPanel panelContenedor)) return;
        for(Component componente : panelContenedor.getComponents()){
            if(!(componente instanceof JPanel fila)) continue;
            Object serverObj = fila.getClientProperty("server");
            if(!(serverObj instanceof Server server)) continue;
            marcarFilaSeleccionada(fila);
            fila.scrollRectToVisible(new Rectangle(0, 0, fila.getWidth(), fila.getHeight()));
            if(listener != null){
                listener.servidorSeleccionado(server);
            }
            return;
        }
    }

    GestorServidores gestorServidores;

    // Constructor de PanelServidores
    PanelServidores(GestorServidores gestorServidores){
        this.gestorServidores = gestorServidores;
        this.setBorder(null);
        this.setMinimumSize(MIN_SIZE);
        refrescarTema(true);
        this.setOpaque(true);
        this.setBackground(bgLista);
        if(this.getViewport() != null){
            this.getViewport().setMinimumSize(MIN_SIZE);
            this.getViewport().setOpaque(true);
            this.getViewport().setBackground(bgLista);
            // BACKINGSTORE fuerza repintado completo del área expuesta, evitando artefactos al cambiar tamaño/selección
            this.getViewport().setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);
        }

        // Scroll más rápido con la rueda del ratón
        try{
            JScrollBar v = this.getVerticalScrollBar();
            if(v != null){
                v.setUnitIncrement(24);
                v.setBlockIncrement(90);
            }
        } catch (Exception ignored){
        }

        UIManager.addPropertyChangeListener(evt -> {
            if(!"lookAndFeel".equals(evt.getPropertyName())) return;
            SwingUtilities.invokeLater(() -> {
                refrescarTema(true);
                if(getViewport() != null){
                    getViewport().setOpaque(true);
                    getViewport().setBackground(bgLista);
                    // mantener el mismo modo al cambiar L&F
                    getViewport().setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);
                }
            });
        });

        gestorServidores.addPropertyChangeListener(evt -> {
            // Garantizamos que cualquier actualización del árbol Swing ocurra en el EDT.
            if(!"listaServidores".equals(evt.getPropertyName())) return;

            @SuppressWarnings("unchecked")
            List<Server> servidores = (List<Server>) evt.getNewValue();

            SwingUtilities.invokeLater(() -> recargarPanel(servidores, gestorServidores));
        });

        this.setViewportView(construirPanelContenedor(gestorServidores.getListaServidores()));
        // Importante: updateUI() puede ser llamado desde el constructor de JScrollPane (super),
        // antes de que se ejecuten los inicializadores de campos. A partir de aquí ya es seguro
        // recalcular bordes/arcos usando UIManager.
        uiReady = true;
    }

    private void recargarPanel(List<Server> servidores, GestorServidores gestorServidores){
        refrescarTema(true);
        Server servidorSeleccionadoActual = obtenerServidorSeleccionadoActual();
        JPanel panelContenedor = construirPanelContenedor(servidores);

        filaSeleccionada = null;
        this.setViewportView(panelContenedor);
        if(servidorSeleccionadoActual != null){
            mostrarSeleccionServidor(servidorSeleccionadoActual);
        }
        this.revalidate();
        this.repaint();
    }

    private JPanel construirPanelContenedor(List<Server> servidores){
        JPanel panelContenedor = new JPanel();
        panelContenedor.setOpaque(true);
        panelContenedor.setBackground(bgLista);
        panelContenedor.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6)); // deja aire para que se vea el radio
        panelContenedor.setLayout(new BoxLayout(panelContenedor, BoxLayout.Y_AXIS));

        if(servidores == null) return panelContenedor;

        for(Server servidor : servidores){
            if(servidor == null || servidor.getVersion() == null) continue;
            JPanel fila = crearFila(servidor, servidor.getVersion());
            panelContenedor.add(fila);
        }
        return panelContenedor;
    }

    private Server obtenerServidorSeleccionadoActual(){
        if(gestorServidores != null && gestorServidores.getServidorSeleccionado() != null){
            return gestorServidores.getServidorSeleccionado();
        }
        if(filaSeleccionada == null) return null;
        Object serverObj = filaSeleccionada.getClientProperty("server");
        return serverObj instanceof Server server ? server : null;
    }

    private JPanel crearFila(Server servidor, String version){
        final int ALTURA_FILA = 90;
        refrescarTema(false);

        RoundedBackgroundPanel fila = new RoundedBackgroundPanel(bgNormal, arc);
        fila.setLayout(new BorderLayout(10,0));
        fila.setBorder(bordeRedondo);
        fila.setBackground(bgNormal);
        fila.setOpaque(false); // el fondo lo pintamos manualmente con bordes redondeados
        fila.setBorder(bordeRedondo);

        // En BoxLayout (Y_AXIS), el ancho se puede estirar si el maxWidth es grande.
        // Dejamos altura fija y ancho flexible para que se adapte al tamaño de la ventana/scroll.
        fila.setPreferredSize(new Dimension(0, ALTURA_FILA));
        fila.setMinimumSize(new Dimension(0, ALTURA_FILA));
        fila.setMaximumSize(new Dimension(Integer.MAX_VALUE, ALTURA_FILA));
        fila.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Imagen del servidor a la izquierda, escalada y redondeada
        ImageIcon icono = servidor.getServerIconOrUseDefault();
        ImagenRedondaLabel iconoRedondo = new ImagenRedondaLabel(icono, 10, 64, 64);

        JPanel panelIcono = new JPanel(new GridBagLayout());
        panelIcono.setOpaque(false); // deja que el clip redondeado del padre marque el fondo
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

        JLabel nombreLabel = new JLabel();
        nombreLabel.setFont(nombreLabel.getFont().deriveFont(Font.BOLD, 16f));
        nombreLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        String nombreServidor = servidor.getDisplayName();
        if(nombreServidor == null || nombreServidor.isBlank()){
            nombreServidor = "(sin nombre)";
        }
        if(Boolean.TRUE.equals(servidor.getFavorito())){
            nombreServidor = "\u2605 " + nombreServidor;
        }
        nombreLabel.putClientProperty("fullText", nombreServidor);

        JLabel motdLabel = new JLabel();
        motdLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        motdLabel.putClientProperty("fullText", Utilidades.leerMotdDesdeProperties(Path.of(servidor.getServerDir())));

        JLabel versionLabel = new JLabel();
        versionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        String versionTexto = (version == null || version.isBlank()) ? "(sin versión)" : ("Versión: " + version);
        versionLabel.putClientProperty("fullText", versionTexto);

        infoPanel.add(nombreLabel);
        infoPanel.add(motdLabel);
        infoPanel.add(versionLabel);

        panelDerecho.add(infoPanel, BorderLayout.NORTH);

        // Indicador de estado (Abajo)
        JLabel estado = new JLabel();
        estado.setFont(estado.getFont().deriveFont(Font.BOLD, 14f)); // que esté bien grande
        boolean vivo = servidor.getServerProcess() != null && servidor.getServerProcess().isAlive();
        // El texto no cambia de color: solo el punto.
        estado.setForeground(AppTheme.getForeground());
        actualizarEstadoLabel(estado, vivo);
        estado.setAlignmentX(Component.LEFT_ALIGNMENT);

        panelDerecho.add(estado, BorderLayout.SOUTH);
        fila.add(panelDerecho, BorderLayout.CENTER);

        // Ajustar los textos al ancho disponible (con "..." si no caben).
        Runnable ajustarTextos = () -> {
            int anchoDisponible = panelDerecho.getWidth();
            if(anchoDisponible <= 0) return;

            // un pequeño margen para que no “choque” con bordes/paddings
            int maxWidth = Math.max(0, anchoDisponible - 10);

            aplicarEllipsisLabel(nombreLabel, maxWidth);
            aplicarEllipsisLabel(motdLabel, maxWidth);
            aplicarEllipsisLabel(versionLabel, maxWidth);
        };

        // Primera vez (cuando Swing ya ha calculado tamaños)
        SwingUtilities.invokeLater(ajustarTextos);

        // Recalcular si cambia el tamaño del contenedor
        panelDerecho.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                ajustarTextos.run();
            }
        });

        // Interacción y propiedades
        fila.putClientProperty("server", servidor);
        fila.putClientProperty("estadoLabel", estado);
        fila.putClientProperty("panelIcono", panelIcono);
        MouseAdapter filaMouse = new MouseAdapter() {
            private void maybeShow(MouseEvent e){
                if(!e.isPopupTrigger()) return;
                        JPopupMenu menu = crearMenuServidor(servidor);
                menu.show(e.getComponent(), e.getX(), e.getY());
            }

            private boolean contiene(MouseEvent e){
                Point p = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), fila);
                return fila.contains(p);
            }

            @Override
            public void mousePressed(MouseEvent e){
                maybeShow(e);
                if(e.isPopupTrigger() || !SwingUtilities.isLeftMouseButton(e)) return;

                refrescarTema(false);
                fila.setBackground(bgPresionado);
                syncIconBg(fila);
                fila.repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e){
                maybeShow(e);
                if(e.isPopupTrigger() || !SwingUtilities.isLeftMouseButton(e)) return;

                refrescarTema(false);
                boolean dentro = contiene(e);
                if(!dentro){
                    if(fila == filaSeleccionada) fila.setBackground(bgSelected);
                    else fila.setBackground(bgNormal);
                    syncIconBg(fila);
                    fila.repaint();
                    return;
                }

                seleccionarFila(fila, true);
            }

            @Override
            public void mouseEntered(MouseEvent e){
                refrescarTema(false);
                if(fila != filaSeleccionada){
                    fila.setBorder(bordeHover);
                    fila.setBackground(bgHover);
                    syncIconBg(fila);
                    fila.repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e){
                refrescarTema(false);
                // Si seguimos dentro de la fila (p.ej. pasando del icono al texto), no "des-hover"
                try{
                    Point p = e.getLocationOnScreen();
                    SwingUtilities.convertPointFromScreen(p, fila);
                    if(fila.contains(p)) return;
                } catch (IllegalComponentStateException ignored){
                    // si no está en pantalla, dejamos que resetee
                }
                if (fila != filaSeleccionada){
                    fila.setBorder(bordeRedondo);
                    fila.setBackground(bgNormal);
                    syncIconBg(fila);
                    fila.repaint();
                }
            }
        };
        hacerFilaClickable(fila, filaMouse);
        fila.setBorder(bordeRedondo);
        return fila;
    }

    private void seleccionarFila(JPanel fila, boolean notificarSiYaEstabaSeleccionada){
        if(fila == null) return;

        Object serverObj = fila.getClientProperty("server");
        if(!(serverObj instanceof Server serverSeleccionado)) return;

        boolean yaEstabaSeleccionada = fila == filaSeleccionada;
        marcarFilaSeleccionada(fila);

        if(listener != null && (!yaEstabaSeleccionada || notificarSiYaEstabaSeleccionada)){
            listener.servidorSeleccionado(serverSeleccionado);
        }
    }

    private JPopupMenu crearMenuServidor(Server server){
        JPopupMenu menu = new JPopupMenu();

        boolean activo = server != null && server.getServerProcess() != null && server.getServerProcess().isAlive();
        String serverDir = server == null ? null : server.getServerDir();
        boolean carpetaDisponible = serverDir != null && !serverDir.isBlank() && new File(serverDir).isDirectory();

        JMenuItem iniciar = new JMenuItem("Iniciar");
        iniciar.setEnabled(!activo);
        iniciar.addActionListener(e -> {
            try {
                gestorServidores.iniciarServidor(server);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
        menu.add(iniciar);

        JMenuItem parar = new JMenuItem("Parar");
        parar.setEnabled(activo);
        parar.addActionListener(e -> gestorServidores.safePararServidor(server));
        menu.add(parar);

        JMenuItem reiniciar = new JMenuItem("Reiniciar");
        reiniciar.setEnabled(activo);
        reiniciar.addActionListener(e -> {
            server.setRestartPending(true);
            gestorServidores.safePararServidor(server);
        });
        menu.add(reiniciar);

        menu.addSeparator();

        JMenuItem configuracion = new JMenuItem("Configuración");
        configuracion.addActionListener(e -> {
            if(contextMenuListener != null){
                contextMenuListener.abrirConfiguracion(server);
            }
        });
        menu.add(configuracion);

        JMenuItem mundos = new JMenuItem("Mundos");
        mundos.addActionListener(e -> {
            if(contextMenuListener != null){
                contextMenuListener.abrirMundos(server);
            }
        });
        menu.add(mundos);

        JMenuItem explorador = new JMenuItem("Abrir en explorador de archivos");
        explorador.setEnabled(carpetaDisponible);
        explorador.addActionListener(e -> abrirEnExplorador(server));
        menu.add(explorador);

        menu.addSeparator();

        boolean esFavorito = Boolean.TRUE.equals(server.getFavorito());
        JMenuItem favorito = new JMenuItem(esFavorito ? "\u2606 Quitar de favoritos" : "\u2605 Marcar como favorito");
        favorito.addActionListener(e -> gestorServidores.establecerFavorito(server, !esFavorito));
        menu.add(favorito);

        return menu;
    }

    private void abrirEnExplorador(Server server){
        if(server == null) return;
        String serverDir = server.getServerDir();
        if(serverDir == null || serverDir.isBlank()){
            JOptionPane.showMessageDialog(this, "El servidor seleccionado no tiene una carpeta v?lida.", "Abrir carpeta", JOptionPane.ERROR_MESSAGE);
            return;
        }

        File carpeta = new File(serverDir);
        if(!carpeta.isDirectory()){
            JOptionPane.showMessageDialog(this, "El servidor seleccionado no tiene una carpeta v?lida.", "Abrir carpeta", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try{
            Desktop.getDesktop().open(carpeta);
        } catch (IOException ex){
            JOptionPane.showMessageDialog(this, "No se ha podido abrir la carpeta: " + ex.getMessage(), "Abrir carpeta", JOptionPane.ERROR_MESSAGE);
        } catch (UnsupportedOperationException ex){
            JOptionPane.showMessageDialog(this, "Este sistema no soporta abrir carpetas desde Java.", "Abrir carpeta", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void hacerFilaClickable(Component c, MouseAdapter adapter){
        if(c == null) return;
        c.addMouseListener(adapter);
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if(c instanceof Container cont){
            for(Component child : cont.getComponents()){
                hacerFilaClickable(child, adapter);
            }
        }
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
            actualizarEstadoLabel(estado, vivo);
        }

    }

    private void actualizarEstadoLabel(JLabel estado, boolean vivo){
        String dotColor = vivo ? "#00C853" : "#D50000";
        String texto = vivo ? "Activo" : "Inactivo";
        estado.setText("<html><span style='color:" + dotColor + ";'>●</span> " + texto + "</html>");
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
        refrescarTema(true);
        if(filaSeleccionada != null){
            filaSeleccionada.setBorder(bordeRedondo);
            filaSeleccionada.setBackground(bgNormal);
            syncIconBg(filaSeleccionada); // mantener el fondo del panel del icono sincronizado con la fila
            filaSeleccionada.repaint();
        }

        filaSeleccionada = fila;
        fila.setBackground(bgSelected);
        fila.setBorder(bordeSeleccionado);
        syncIconBg(fila); // mantener el fondo del panel del icono sincronizado con la fila

        fila.repaint();
    }

    private void syncIconBg(JPanel fila){ // actualiza el fondo del contenedor del icono para evitar "ghosting" al repintar/redimensionar
        if(fila == null) return;
        Object panelIconoObj = fila.getClientProperty("panelIcono");
        if(!(panelIconoObj instanceof JComponent panelIcono)) return;
        // Mantener el fondo transparente evita que sobresalga por los bordes redondeados
        panelIcono.setOpaque(false);
        panelIcono.repaint();
    }

    private void aplicarEllipsisLabel(JLabel label, int maxWidthPx){
        Object fullObj = label.getClientProperty("fullText");
        String full = (fullObj == null) ? "" : String.valueOf(fullObj);
        full = full.strip();

        FontMetrics fm = label.getFontMetrics(label.getFont());
        label.setText(ellipsizePx(full, fm, maxWidthPx));
    }

    // Si el texto no entra (en píxeles) añade "..."
    private String ellipsizePx(String s, FontMetrics fm, int maxWidthPx){
        if(s == null) return "";
        if(maxWidthPx <= 0) return "";

        String dots = "...";
        if(fm.stringWidth(s) <= maxWidthPx) return s;
        if(fm.stringWidth(dots) >= maxWidthPx) return dots;

        int lo = 0;
        int hi = s.length();
        while(lo < hi){
            int mid = (lo + hi + 1) >>> 1;
            String candidate = s.substring(0, mid) + dots;
            if(fm.stringWidth(candidate) <= maxWidthPx) lo = mid;
            else hi = mid - 1;
        }
        return s.substring(0, lo) + dots;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        // Mantener este scrollpane sin borde aunque cambie el tema (super.updateUI() puede restaurar el borde por defecto).
        setBorder(null);
        setViewportBorder(null);
        if (!uiReady) return;
        // updateComponentTreeUI() actualiza los UI delegates, pero los bordes/colores creados a mano
        // (FlatLineBorder con arc/color calculados) no se recalculan solos: los re-aplicamos.
        refrescarTema(true);
    }

    private Color oscurecer(Color c, float amount) {
        return AppTheme.darken(c, amount);
    }

    private Color tintar(Color accent, Color base, float t) {
        return AppTheme.tint(base, accent, t);
    }

    private void refrescarTema(boolean aplicarEnFilas){
        arc = AppTheme.getArc();

        acento = AppTheme.getMainAccent();
        base = AppTheme.getBorderColor();

        bgLista = AppTheme.getPanelBackground();
        bgNormal = AppTheme.getBackground();
        bgHover = AppTheme.tint(bgNormal, acento, 0.08f);
        bgPresionado = AppTheme.tint(bgNormal, acento, 0.14f);
        bgSelected = AppTheme.tint(bgNormal, acento, 0.18f);

        bordeRedondo = AppTheme.createRoundedBorder(insets, 1f);
        bordeHover = AppTheme.createAccentBorder(insets, 1f);
        bordeSeleccionado = AppTheme.createAccentBorder(insets, 3f);

        setBackground(bgLista);
        if(getViewport() != null){
            getViewport().setOpaque(true);
            getViewport().setBackground(bgLista);
        }

        if(!aplicarEnFilas) return;
        Component vista = getViewport().getView();
        if(!(vista instanceof JPanel panelContenedor)) return;

        panelContenedor.setOpaque(true);
        panelContenedor.setBackground(bgLista);

        for(Component componente : panelContenedor.getComponents()){
            if(!(componente instanceof JPanel fila)) continue;
            if(fila instanceof RoundedBackgroundPanel rbp) rbp.setArc(arc); // mantiene mismo radio que el borde
            if(fila == filaSeleccionada){
                fila.setBorder(bordeSeleccionado);
                fila.setBackground(bgSelected);
            } else {
                fila.setBorder(bordeRedondo);
                fila.setBackground(bgNormal);
            }
            syncIconBg(fila); // sincroniza el panel del icono con el fondo nuevo del tema
            fila.repaint();
        }

        panelContenedor.repaint();
        repaint();
    }

}
