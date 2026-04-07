/*
 * Fichero: VentanaPrincipal.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 * Fecha: 17/01/2026
 *
 * Descripción:
 * Esta es la ventana principal, extiende de JFrame y muestra todo el contenido del servidor, es el encargado de mostrar
 * todos los paneles y es la ventana con la que el usuario más va a interactuar.
 * */

package vista;

import controlador.GestorServidores;
import controlador.Main;
import modelo.Server;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.ui.FlatLineBorder;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

public class VentanaPrincipal extends JFrame {
    private static final String PROP_MANAGED_ROUNDED_BORDER = "easy-mc-server.managedRoundedBorder";
    private static final String PROP_ROUNDED_BORDER_ENABLED = "easy-mc-server.roundedBorderEnabled";

    private final JPanel panelIzquierdo, panelDerecho;
    private final GestorServidores gestorServidores;
    private TitledCardPanel servidoresCard; // card de la izquierda con borde redondeado
    private JPanel servidoresPanel;
    private Server serverMostrado;
    private Consumer<String> consoleListenerActual;
    private JButton abrirCarpetaServerButton;
    private JButton borrarServerButton;
    private JButton nuevoServerButton;
    private JButton importarServerButton;
    private JPanel botonesServidoresPanel;
    private final PanelServidores listaServidoresPanel;
    private boolean cambiandoANoServerDialog = false;
    private JSplitPane splitPrincipal;
    private JSplitPane splitHome;
    private PanelConfigServidor panelConfigServidor;

    private enum PaginaDerecha { HOME, MUNDO, CONFIG, MODS, INFO }
    private record TemaInfo(String name, String className){}
    private PaginaDerecha paginaDerechaActual = PaginaDerecha.HOME;
    private JPanel panelDerechoCards;
    private CardLayout cardDerecho;
    private JPanel panelBarraVertical;
    private TitledCardPanel jugadoresCard; // card dentro del split del HOME
    private JPanel consolaCard; // card dentro del split del HOME
    private final Map<PaginaDerecha, JButton> navButtons = new EnumMap<>(PaginaDerecha.class);

    public VentanaPrincipal(GestorServidores gestorServidores) {
        this.gestorServidores = gestorServidores;
        this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        this.setSize(1280, 720);
        this.setTitle("Easy-MC-Server");
        this.setLocationRelativeTo(null);
        JPanel ventanaPrincipalPanel = new JPanel(new BorderLayout()); // el panel principal, donde se aloja todo
        Color bgApp = AppTheme.getBackground();
        Color panelBg = AppTheme.getPanelBackground();
        ventanaPrincipalPanel.setBackground(bgApp);
        this.setContentPane(ventanaPrincipalPanel);

        // PANEL IZQUIERDO ---------------------------------------------------------------------------------------------
        panelIzquierdo = new JPanel(new BorderLayout()); // este es un panel auxiliar que engloba toda la parte izq
        panelIzquierdo.setOpaque(true);
        panelIzquierdo.setBackground(bgApp);

        // PANEL DE GRÁFICAS DE RENDIMIENTO
        JPanel rendimientoPanel = new JPanel(new GridLayout(1,3));
        panelIzquierdo.add(rendimientoPanel, BorderLayout.NORTH);

        // PANEL DE SERVIDORES (card con borde redondeado)
        servidoresCard = new TitledCardPanel("Lista de servidores", new Insets(8, 8, 8, 8));
        servidoresCard.setBorder(BorderFactory.createEmptyBorder());
        panelIzquierdo.add(servidoresCard, BorderLayout.CENTER);

        servidoresPanel = new JPanel(new BorderLayout());
        servidoresPanel.setOpaque(true); // rellena el fondo bajo el listado para evitar "ghosting"
        servidoresPanel.setBackground(panelBg);
        servidoresCard.getContentPanel().add(servidoresPanel, BorderLayout.CENTER);

        // PANEL DE LISTADO DE SERVIDORES
        listaServidoresPanel = getPanelServidores(gestorServidores);
        servidoresPanel.add(listaServidoresPanel, BorderLayout.CENTER);

        // PANEL DE BOTONES DE SERVIDORES
        botonesServidoresPanel = new JPanel(new GridLayout(1,4));
        botonesServidoresPanel.setOpaque(true);
        botonesServidoresPanel.setBackground(panelBg);
        servidoresPanel.add(botonesServidoresPanel, BorderLayout.SOUTH);

        nuevoServerButton = new JButton("+");
        importarServerButton = new JButton("↓");
        importarServerButton.setToolTipText("Importar servidor");
        borrarServerButton = new JButton("-");
        borrarServerButton.setToolTipText("Eliminar servidor");
        abrirCarpetaServerButton = new JButton("📁");
        abrirCarpetaServerButton.setToolTipText("Abrir carpeta del servidor");
        abrirCarpetaServerButton.setEnabled(false);
        borrarServerButton.setEnabled(false);

        // Que se vean más: texto en negrita + más grande + más padding
        estilizarBoton(nuevoServerButton);
        estilizarBoton(importarServerButton);
        estilizarBoton(borrarServerButton);
        estilizarBoton(abrirCarpetaServerButton);

        botonesServidoresPanel.add(nuevoServerButton);
        botonesServidoresPanel.add(importarServerButton);
        botonesServidoresPanel.add(borrarServerButton);
        botonesServidoresPanel.add(abrirCarpetaServerButton);

        nuevoServerButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Server server = gestorServidores.crearServidor();
                if(server != null){
                    seleccionarServidor(server);
                }
            }
        });
        importarServerButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Server server = gestorServidores.importarServidor();
                if(server != null){
                    seleccionarServidor(server);
                }
            }
        });
        borrarServerButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Server server = gestorServidores.getServidorSeleccionado();
                if(server == null){
                    borrarServerButton.setEnabled(false);
                    abrirCarpetaServerButton.setEnabled(false);
                    return;
                }
                if(server.getServerProcess() != null && server.getServerProcess().isAlive()){
                    JOptionPane.showMessageDialog(
                            VentanaPrincipal.this,
                            "No puedes eliminar un servidor mientras esta activo. Paralo antes de eliminarlo.",
                            "Servidor activo",
                            JOptionPane.WARNING_MESSAGE
                    );
                    return;
                }

                int confirm = JOptionPane.showConfirmDialog(
                        VentanaPrincipal.this,
                        "¿Seguro que quieres eliminar '" + server.getDisplayName() + "' de la lista?\n(No borra la carpeta del disco)",
                        "Eliminar servidor",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if(confirm != JOptionPane.YES_OPTION) return;

                List<Server> antes = gestorServidores.getListaServidores() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(gestorServidores.getListaServidores());
                Server candidato = null;
                for(int i = 0; i < antes.size(); i++){
                    Server s = antes.get(i);
                    if(s != null && s.getId() != null && s.getId().equals(server.getId())){
                        if(i > 0) candidato = antes.get(i - 1);
                        else if(i + 1 < antes.size()) candidato = antes.get(i + 1);
                        break;
                    }
                }

                boolean eliminado = gestorServidores.eliminarServidor(server);
                if(eliminado){
                    gestorServidores.setServidorSeleccionado(null);
                    panelDerecho.removeAll();
                    panelDerecho.revalidate();
                    panelDerecho.repaint();
                    borrarServerButton.setEnabled(false);
                    abrirCarpetaServerButton.setEnabled(false);

                    if(gestorServidores.getListaServidores() == null || gestorServidores.getListaServidores().isEmpty()){
                        volverANoServerDialog(gestorServidores);
                    } else {
                        Server finalCandidato = candidato;
                        SwingUtilities.invokeLater(() -> {
                            if(finalCandidato != null){
                                seleccionarServidor(finalCandidato);
                            } else {
                                listaServidoresPanel.seleccionarPrimero();
                            }
                        });
                    }
                } else {
                    JOptionPane.showMessageDialog(
                            VentanaPrincipal.this,
                            "No se ha podido eliminar el servidor.",
                            "Eliminar servidor",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        });
        abrirCarpetaServerButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Server server = gestorServidores.getServidorSeleccionado();
                if(server == null){
                    JOptionPane.showMessageDialog(
                            VentanaPrincipal.this,
                            "No hay ningún servidor seleccionado.",
                            "Abrir carpeta",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                    return;
                }
                if(server.getServerDir() == null || server.getServerDir().isBlank()){
                    JOptionPane.showMessageDialog(
                            VentanaPrincipal.this,
                            "El servidor seleccionado no tiene una carpeta válida.",
                            "Abrir carpeta",
                            JOptionPane.ERROR_MESSAGE
                    );
                    return;
                }
                try{
                    Desktop.getDesktop().open(new File(server.getServerDir()));
                } catch (IOException ex){
                    JOptionPane.showMessageDialog(
                            VentanaPrincipal.this,
                            "No se ha podido abrir la carpeta: " + ex.getMessage(),
                            "Abrir carpeta",
                            JOptionPane.ERROR_MESSAGE
                    );
                } catch (UnsupportedOperationException ex){
                    JOptionPane.showMessageDialog(
                            VentanaPrincipal.this,
                            "Este sistema no soporta abrir carpetas desde Java.",
                            "Abrir carpeta",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        });

        new Timer(1000, e -> {
            // actualizar estado cada 1 segundo
            listaServidoresPanel.actualizarEstados();
        }).start();

        // Si se vacia la lista por cualquier via, volvemos al dialogo inicial
        gestorServidores.addPropertyChangeListener("listaServidores", evt -> {
            Object nuevo = evt.getNewValue();
            if(!(nuevo instanceof java.util.List<?> lista)) return;
            if(!lista.isEmpty()) return;
            SwingUtilities.invokeLater(() -> volverANoServerDialog(gestorServidores));
        });

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                intentarCerrarAplicacion(gestorServidores);
            }
        });


        // PANEL DERECHO -----------------------------------------------------------------------------------------------

        panelDerecho = new JPanel(new BorderLayout()); // este es un panel auxiliar que engloba toda la parte dcha
        panelDerecho.setOpaque(true);
        panelDerecho.setBackground(bgApp);

        // Cards (contenido del panel derecho)
        cardDerecho = new CardLayout();
        panelDerechoCards = new JPanel(cardDerecho);
        panelDerechoCards.setOpaque(true);
        panelDerechoCards.setBackground(bgApp);
        panelDerecho.add(panelDerechoCards, BorderLayout.CENTER);

        // Barra vertical de navegación (va a la IZQUIERDA de la ventana, fuera del split)
        panelBarraVertical = crearBarraVertical();

        // SPLIT PANE

        JPanel wrapperIzquierdo = new JPanel(new BorderLayout());
        wrapperIzquierdo.add(panelIzquierdo, BorderLayout.CENTER);
        wrapperIzquierdo.setOpaque(true);
        wrapperIzquierdo.setBackground(bgApp);
        wrapperIzquierdo.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 0));

        JPanel wrapperDerecho = new JPanel(new BorderLayout());
        wrapperDerecho.add(panelDerecho, BorderLayout.CENTER);
        wrapperDerecho.setOpaque(true);
        wrapperDerecho.setBackground(bgApp);
        wrapperDerecho.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 8));

        splitPrincipal = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, wrapperIzquierdo, wrapperDerecho);
        configurarSplitPane(splitPrincipal, 8);
        splitPrincipal.setResizeWeight(0.1);
        splitPrincipal.setOpaque(true);
        splitPrincipal.setBackground(bgApp);

        // Barra vertical al extremo izquierdo de la ventana
        JPanel barraWrapper = new JPanel(new BorderLayout());
        barraWrapper.setOpaque(true);
        barraWrapper.setBackground(bgApp);
        barraWrapper.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 0));
        barraWrapper.add(panelBarraVertical, BorderLayout.CENTER);
        ventanaPrincipalPanel.add(barraWrapper, BorderLayout.WEST);
        ventanaPrincipalPanel.add(splitPrincipal, BorderLayout.CENTER);

        // Fuerza un repintado completo al redimensionar para evitar artefactos visuales con componentes transparentes
        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                SwingUtilities.invokeLater(() -> {
                    JRootPane root = getRootPane();
                    if(root != null){
                        RepaintManager.currentManager(root).markCompletelyDirty(root);
                        root.revalidate();
                        root.repaint();
                    }
                });
            }
        });


        //ventanaPrincipalPanel.add(panelDerecho, BorderLayout.EAST);

        // Seleccion inicial (cuando ya están creados botones y panel derecho)
        SwingUtilities.invokeLater(() -> {
            if(gestorServidores.getServidorSeleccionado() == null
                    && gestorServidores.getListaServidores() != null
                    && !gestorServidores.getListaServidores().isEmpty()){
                listaServidoresPanel.seleccionarPrimero();
            }
        });
    }

    public VentanaPrincipal(GestorServidores gestorServidores, Server servidorASeleccionar){
        this(gestorServidores);
        if(servidorASeleccionar != null){
            SwingUtilities.invokeLater(() -> seleccionarServidor(servidorASeleccionar));
        }
    }

    private void mostrarPanelDerecho(Server server, GestorServidores gestorServidores){
        panelDerechoCards.removeAll();

        JPanel home = new JPanel(new BorderLayout(0, 8));
        home.setOpaque(false);

        PanelTotalServidor panelTotalServidor = new PanelTotalServidor(gestorServidores);
        PanelJugadores panelJugadores = new PanelJugadores(gestorServidores, false);
        PanelConsola panelConsola = new PanelConsola(gestorServidores);
        panelConsola.setPreferredSize(new Dimension(this.getWidth(), 100));

        // Todo lo que está encima de los jugadores en un "card" con borde redondeado (FlatLaf)
        TitledCardPanel headerCard = new TitledCardPanel("Servidor seleccionado", new Insets(8, 8, 8, 8));
        headerCard.setBorder(BorderFactory.createEmptyBorder());
        headerCard.getContentPanel().add(panelTotalServidor, BorderLayout.CENTER);

        home.add(headerCard, BorderLayout.NORTH);

        // Jugadores y consola con splitpane y bordes redondeados
        jugadoresCard = new TitledCardPanel("Jugadores", new Insets(8, 8, 8, 8));
        jugadoresCard.setBorder(BorderFactory.createEmptyBorder());
        jugadoresCard.getContentPanel().add(panelJugadores, BorderLayout.CENTER);

        consolaCard = new JPanel(new BorderLayout());
        consolaCard.setOpaque(false);
        consolaCard.setBorder(null); // normalmente sin borde
        setBordeRedondoGestionado(consolaCard, false);
        consolaCard.add(panelConsola, BorderLayout.CENTER);

        splitHome = new JSplitPane(JSplitPane.VERTICAL_SPLIT, jugadoresCard, consolaCard);
        configurarSplitPane(splitHome, 8);
        splitHome.setResizeWeight(0.7);

        home.add(splitHome, BorderLayout.CENTER);

        // Pintar historial actual
        panelConsola.actualizarConsola();

        // Desenganchar listener del server anterior
        if(serverMostrado != null && consoleListenerActual != null){
            serverMostrado.removeConsoleListener(consoleListenerActual);
        }

        // Reenganchar listener
        consoleListenerActual = panelConsola::escribirLinea;
        server.addConsoleListener(consoleListenerActual);
        serverMostrado = server;

        panelConfigServidor = new PanelConfigServidor(gestorServidores);
        JPanel mundo = new PanelMundo(gestorServidores, () -> {
            if(panelConfigServidor != null){
                panelConfigServidor.reload();
            }
        });
        JPanel config = panelConfigServidor;
        JPanel mods = new JPanel(new BorderLayout());
        mods.setOpaque(false);
        JPanel info = new JPanel(new BorderLayout());
        info.setOpaque(false);

        panelDerechoCards.add(home, PaginaDerecha.HOME.name());
        panelDerechoCards.add(mundo, PaginaDerecha.MUNDO.name());
        panelDerechoCards.add(config, PaginaDerecha.CONFIG.name());
        panelDerechoCards.add(mods, PaginaDerecha.MODS.name());
        panelDerechoCards.add(info, PaginaDerecha.INFO.name());

        PaginaDerecha paginaAMostrar = paginaDerechaActual != null ? paginaDerechaActual : PaginaDerecha.HOME;
        setPaginaDerecha(paginaAMostrar);
        panelDerechoCards.revalidate();
        panelDerechoCards.repaint();
    }

    private JPanel crearBarraVertical(){

        JPanel barra = new CardPanel(new BorderLayout(), new Insets(6, 6, 6, 6));
        barra.setBackground(AppTheme.getPanelBackground());
        barra.setPreferredSize(new Dimension(56, 0)); // más estrecha

        JPanel botones = new JPanel();
        botones.setOpaque(false);
        botones.setLayout(new BoxLayout(botones, BoxLayout.Y_AXIS));

        JButton home = crearNavButton("\uD83C\uDFE0", "Home", PaginaDerecha.HOME);
        JButton mundo = crearNavButton("\uD83C\uDF0D", "Mundos", PaginaDerecha.MUNDO);
        JButton config = crearNavButton("\u2699", "Configuración del servidor", PaginaDerecha.CONFIG);
        JButton mods = crearNavButton("▣", "Mods (Sin implementar)", PaginaDerecha.MODS);
        JButton temas = crearActionButton("\uD83D\uDD8C", "Apariencia", this::abrirSelectorTema);
        JButton info = crearNavButton("\u2139", "Información", PaginaDerecha.INFO);

        botones.add(home);
        botones.add(Box.createVerticalStrut(8));
        botones.add(mundo);
        botones.add(Box.createVerticalStrut(8));
        botones.add(config);
        botones.add(Box.createVerticalStrut(8));
        botones.add(mods);
        botones.add(Box.createVerticalGlue()); // fijar los siguientes al fondo
        botones.add(temas); // abajo
        botones.add(Box.createVerticalStrut(6));
        botones.add(info); // pegado debajo del pincel
        botones.add(Box.createVerticalStrut(4)); // pequeño margen inferior

        // Hover: se calcula en el momento para que, si el usuario cambia de tema, coja el nuevo arc y color.
        MouseAdapter navHover = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                Object src = e.getSource();
                if(!(src instanceof JButton b)) return;
                if(b.getBackground() != null && b.isOpaque()) return; // ya seleccionado: no alterar
                Color hoverSeleccion = colorSeleccionPanelServidores();
                b.setOpaque(true);
                b.setContentAreaFilled(true);
                b.setBackground(hoverSeleccion);
                b.setBorder(new FlatLineBorder(new Insets(6,6,6,6), AppTheme.getMainAccent(), 1f, AppTheme.getArc()));
                b.repaint();
            }
            @Override public void mouseExited(MouseEvent e) {
                setPaginaDerecha(paginaDerechaActual); // restablecer estado real
            }
        };
        home.addMouseListener(navHover);
        mundo.addMouseListener(navHover);
        config.addMouseListener(navHover);
        mods.addMouseListener(navHover);
        info.addMouseListener(navHover);

        MouseAdapter temasHover = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                Color hoverSeleccion = colorSeleccionPanelServidores();
                temas.setOpaque(true);
                temas.setContentAreaFilled(true);
                temas.setBackground(hoverSeleccion);
                temas.setBorder(new FlatLineBorder(new Insets(6,6,6,6), AppTheme.getMainAccent(), 1f, AppTheme.getArc()));
                temas.repaint();
            }
            @Override public void mouseExited(MouseEvent e) {
                temas.setOpaque(false);
                temas.setContentAreaFilled(false);
                temas.setBackground(null);
                temas.setBorder(new FlatLineBorder(new Insets(6,6,6,6), AppTheme.getTransparentColor(), 1f, AppTheme.getArc()));
                temas.repaint();
            }
        };
        temas.addMouseListener(temasHover);

        barra.add(botones, BorderLayout.CENTER);
        return barra;
    }

    private JButton crearNavButton(String emoji, String tooltip, PaginaDerecha pagina){
        JButton b = new JButton(emoji);
        b.setFocusPainted(false);
        b.setAlignmentX(Component.LEFT_ALIGNMENT); // fijar a la izquierda
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        b.setToolTipText(tooltip);
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 18f));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorderPainted(true); // permitimos dibujar el borde en hover/seleccion
        b.setContentAreaFilled(false); // sin color de fondo por defecto
        b.setOpaque(false); // se pintará sólo cuando esté seleccionado
        b.setBorder(new FlatLineBorder(new Insets(6,6,6,6), AppTheme.getTransparentColor(), 1f, AppTheme.getArc())); // mantiene tamaño estable y permite mostrar borde en hover
        b.putClientProperty("JButton.buttonType", "roundRect"); // mantener esquinas redondeadas con FlatLaf
        b.addActionListener(e -> navegarAPaginaDerecha(pagina));
        navButtons.put(pagina, b);
        return b;
    }

    private JButton crearActionButton(String emoji, String tooltip, Runnable action){
        JButton b = new JButton(emoji);
        b.setFocusPainted(false);
        b.setAlignmentX(Component.LEFT_ALIGNMENT); // fijar a la izquierda
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        b.setToolTipText(tooltip);
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 18f));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorderPainted(false); // sin borde
        b.setContentAreaFilled(false); // fondo transparente
        b.setOpaque(false);
        b.setBorder(new FlatLineBorder(new Insets(6,6,6,6), AppTheme.getTransparentColor(), 1f, AppTheme.getArc()));
        b.putClientProperty("JButton.buttonType", "roundRect"); // borde redondeado al seleccionar
        b.addActionListener(e -> {
            if(action != null) action.run();
        });
        return b;
    }

    private void setPaginaDerecha(PaginaDerecha pagina){
        if(pagina == null) pagina = PaginaDerecha.HOME;
        paginaDerechaActual = pagina;
        cardDerecho.show(panelDerechoCards, pagina.name());

        Color acento = AppTheme.getMainAccent();
        Color bg = AppTheme.getPanelBackground();
        Color fg = AppTheme.getForeground();
        int arc = AppTheme.getArc();

        for(Map.Entry<PaginaDerecha, JButton> e : navButtons.entrySet()){
            JButton b = e.getValue();
            boolean sel = e.getKey() == pagina;
            b.setOpaque(sel); // sólo se pinta el fondo cuando está seleccionado
            b.setContentAreaFilled(sel);
            b.setBackground(sel ? acento : bg);
            b.setForeground(sel ? Color.WHITE : fg);
            // conservar borde redondeado al seleccionar
            b.setBorder(sel
                    ? new FlatLineBorder(new Insets(6,6,6,6), acento, 1f, arc)
                    : new FlatLineBorder(new Insets(6,6,6,6), AppTheme.getTransparentColor(), 1f, arc));
        }
    }

    // Calcula el mismo color de selección usado en PanelServidores (tinte del acento sobre el fondo)
    private Color colorSeleccionPanelServidores(){
        return AppTheme.getSelectionBackground();
    }

    private void abrirSelectorTema(){
        SwingUtilities.invokeLater(() -> {
            java.util.List<TemaInfo> disponibles = new ArrayList<>();

            // 1) Preferimos la lista completa de temas de FlatLaf (vía reflexión para evitar dependencias de método)
            try{
                Class<?> all = Class.forName("com.formdev.flatlaf.intellijthemes.FlatAllIJThemes");
                java.lang.reflect.Field infosField = all.getField("INFOS");
                Object infosObj = infosField.get(null);
                if(infosObj instanceof Object[] arr){
                    for(Object o : arr){
                        if(o == null) continue;
                        String name = null;
                        String className = null;
                        try{
                            java.lang.reflect.Method m = o.getClass().getMethod("getName");
                            Object r = m.invoke(o);
                            if(r != null) name = String.valueOf(r);
                        } catch (Throwable ignored){}
                        try{
                            java.lang.reflect.Method m = o.getClass().getMethod("getClassName");
                            Object r = m.invoke(o);
                            if(r != null) className = String.valueOf(r);
                        } catch (Throwable ignored){}
                        try{
                            if(className == null){
                                java.lang.reflect.Method m = o.getClass().getMethod("getLookAndFeelClassName");
                                Object r = m.invoke(o);
                                if(r != null) className = String.valueOf(r);
                            }
                        } catch (Throwable ignored){}
                        if(name == null || className == null) continue;
                        disponibles.add(new TemaInfo(name, className));
                    }
                }
            } catch (Throwable ignored){
                // fallback abajo
            }

            // 2) Fallback: los LAF instalados en UIManager
            if(disponibles.isEmpty()){
                for(UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()){
                    if(info == null) continue;
                    String cn = info.getClassName();
                    if(cn == null) continue;
                    if(cn.startsWith("com.formdev.flatlaf.intellijthemes.") || cn.startsWith("com.formdev.flatlaf.themes.") || cn.startsWith("com.formdev.flatlaf.")){
                        disponibles.add(new TemaInfo(info.getName(), cn));
                    }
                }
            }

            if(disponibles.isEmpty()){
                JOptionPane.showMessageDialog(this, "No se han encontrado temas de FlatLaf.", "Temas", JOptionPane.WARNING_MESSAGE);
                return;
            }

            disponibles.sort(Comparator.comparing(TemaInfo::name, String.CASE_INSENSITIVE_ORDER));

            String current = UIManager.getLookAndFeel() != null ? UIManager.getLookAndFeel().getName() : null;
            String[] nombres = new String[disponibles.size()];
            for(int i = 0; i < disponibles.size(); i++){
                nombres[i] = disponibles.get(i).name();
            }

            String elegido = (String) JOptionPane.showInputDialog(
                    this,
                    "Selecciona un tema:",
                    "Temas",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    nombres,
                    current
            );
            if(elegido == null) return;

            String className = null;
            for(TemaInfo info : disponibles){
                if(info != null && elegido.equals(info.name())){
                    className = info.className();
                    break;
                }
            }
            if(className == null) return;

            try{
                UIManager.setLookAndFeel(className);
                // Al cambiar de LAF, los defaults de UIManager se reinician; reaplicamos nuestros ajustes
                // para que los bordes/arcos no cambien o desaparezcan.
                Main.aplicarPreferenciasUI();
                FlatLaf.updateUI();
                for(Window w : Window.getWindows()){
                    SwingUtilities.updateComponentTreeUI(w);
                    w.invalidate();
                    w.validate();
                    w.repaint();
                }
                // FlatLaf puede reponer la UI del splitpane; la reconfiguramos
                reconfigurarSplitPanes();
                // Los bordes redondeados creados a mano (FlatLineBorder) no se recalculan solos.
                // Al cambiar de tema, volvemos a generar esos bordes con los nuevos valores de UIManager.
                refreshThemeStyles();
            } catch (Exception ex){
                JOptionPane.showMessageDialog(this, "No se pudo aplicar el tema: " + ex.getMessage(), "Temas", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static void aplicarBordeRedondoGestionado(JComponent c, Insets insets, Color borderColor, float thickness, int arc){
        if(c == null) return;
        if(!Boolean.TRUE.equals(c.getClientProperty(PROP_MANAGED_ROUNDED_BORDER))) return;

        Object enabledObj = c.getClientProperty(PROP_ROUNDED_BORDER_ENABLED);
        boolean enabled = !(enabledObj instanceof Boolean b) || b;

        Border b = c.getBorder();
        if(!enabled){
            // Si el borde estaba desactivado (p.ej. setBorder(null)), quitamos el borde que el tema pueda reponer
            // al cambiar de Look&Feel, para mantener exactamente el mismo aspecto.
            if(b == null) return;
            if(b instanceof FlatLineBorder || b instanceof UIResource){
                c.setBorder(null);
            }
            return;
        }

        // Tras cambiar de tema, Swing/FlatLaf puede restaurar bordes UIResource o dejarlos a null.
        // Reponemos nuestro borde redondeado para mantener el mismo aspecto.
        if(b == null || b instanceof UIResource){
            c.setBorder(new FlatLineBorder(insets, borderColor, thickness, arc));
            return;
        }
        // Si ya es nuestro tipo de borde, lo regeneramos para asegurar que siga teniendo el arc correcto.
        if(b instanceof FlatLineBorder){
            c.setBorder(new FlatLineBorder(insets, borderColor, thickness, arc));
        }
    }

    private static void setBordeRedondoGestionado(JComponent c, boolean enabled){
        if(c == null) return;
        c.putClientProperty(PROP_MANAGED_ROUNDED_BORDER, Boolean.TRUE);
        c.putClientProperty(PROP_ROUNDED_BORDER_ENABLED, enabled);
    }

    private void refreshThemeStyles() {
        Color bgApp = AppTheme.getBackground();
        Color panelBg = AppTheme.getPanelBackground();

        Container cp = getContentPane();
        if (cp != null) cp.setBackground(bgApp);
        if (panelIzquierdo != null) panelIzquierdo.setBackground(bgApp);
        if (panelDerecho != null) panelDerecho.setBackground(bgApp);
        if (panelDerechoCards != null) {
            panelDerechoCards.setOpaque(true);
            panelDerechoCards.setBackground(bgApp);
        }

        int arc = AppTheme.getArc();
        Color borderColor = AppTheme.getBorderColor();

        if (servidoresCard != null) {
            servidoresCard.getCard().setBackground(panelBg);
            servidoresCard.getCard().setBorder(AppTheme.createRoundedBorder(new Insets(8, 8, 8, 8), borderColor, 1f));
        }
        if (servidoresPanel != null) {
            servidoresPanel.setBackground(panelBg);
        }
        if (botonesServidoresPanel != null) {
            botonesServidoresPanel.setBackground(panelBg);
        }
        if (nuevoServerButton != null) nuevoServerButton.setBackground(bgApp);
        if (importarServerButton != null) importarServerButton.setBackground(bgApp);
        if (borrarServerButton != null) borrarServerButton.setBackground(bgApp);
        if (abrirCarpetaServerButton != null) abrirCarpetaServerButton.setBackground(bgApp);
        if (panelBarraVertical != null) {
            panelBarraVertical.setBackground(panelBg);
            aplicarBordeRedondoGestionado(panelBarraVertical, new Insets(6, 6, 6, 6), borderColor, 1f, arc);
        }
        if (jugadoresCard != null) {
            jugadoresCard.getCard().setBackground(panelBg);
            jugadoresCard.getCard().setBorder(AppTheme.createRoundedBorder(new Insets(8, 8, 8, 8), borderColor, 1f));
        }
        if (consolaCard != null) {
            aplicarBordeRedondoGestionado(consolaCard, new Insets(8, 8, 8, 8), borderColor, 1f, arc);
        }

        // Recalcula colores y bordes de los botones de navegación según el tema actual.
        setPaginaDerecha(paginaDerechaActual);
        // Fuerza a que el listado recalcule bordes/colores (también lo hace en updateUI()).
        if (listaServidoresPanel != null) {
            listaServidoresPanel.updateUI();
            listaServidoresPanel.repaint();
        }

        // El HOME se construye con varios componentes y scrolls personalizados. Si el tema cambia,
        // reconstruimos esa zona igual que cuando el usuario cambia de servidor, que es justo cuando
        // el borde rectangular desaparece.
        if (serverMostrado != null && this.gestorServidores != null && panelDerechoCards != null) {
            PaginaDerecha paginaActual = paginaDerechaActual;
            mostrarPanelDerecho(serverMostrado, this.gestorServidores);
            setPaginaDerecha(paginaActual);
        }
    }

    private void configurarSplitPane(JSplitPane split, int dividerSize){
        if(split == null) return;
        split.setContinuousLayout(true);
        split.setOneTouchExpandable(false);
        split.setBorder(null);
        split.setDividerSize(dividerSize);
        split.setUI(new NoGripSplitPaneUI());
        Color bg = AppTheme.getBackground();
        if(bg != null) split.setBackground(bg);

        // Forzar repintado completo cuando se mueve el divisor (evita artefactos de blit)
        if(split.getClientProperty("fullRepaintOnDivider") == null){
            split.putClientProperty("fullRepaintOnDivider", Boolean.TRUE);
            split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
                JRootPane root = getRootPane();
                if(root == null) return;
                RepaintManager.currentManager(root).markCompletelyDirty(root);
                root.repaint();
            });
        }
    }

    private void reconfigurarSplitPanes(){
        SwingUtilities.invokeLater(() -> {
            if(splitPrincipal != null) configurarSplitPane(splitPrincipal, 8);
            if(splitHome != null) configurarSplitPane(splitHome, 8);
        });
    }

    private void seleccionarServidor(Server server){
        if(server == null) return;
        if(!aplicarCambioServidor(server, false)) return;
        listaServidoresPanel.mostrarSeleccionServidor(server);
    }

    private void volverANoServerDialog(GestorServidores gestorServidores){
        if(cambiandoANoServerDialog) return;
        if(!isDisplayable()) return;
        if(!confirmarSalidaConfiguracion(null, null)) return;
        cambiandoANoServerDialog = true;

        if(serverMostrado != null && consoleListenerActual != null){
            serverMostrado.removeConsoleListener(consoleListenerActual);
        }
        serverMostrado = null;
        consoleListenerActual = null;

        NoServerFrame dialog = new NoServerFrame(gestorServidores);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        this.dispose();
    }

    private void intentarCerrarAplicacion(GestorServidores gestorServidores){
        if(!confirmarSalidaConfiguracion(null, null)) return;

        List<Server> activos = gestorServidores.getServidoresActivos();
        if(activos.isEmpty()){
            this.dispose();
            System.exit(0);
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Hay servidores activos.\n\n");
        for(Server s : activos){
            String nombre = (s.getDisplayName() == null || s.getDisplayName().isBlank()) ? s.getId() : s.getDisplayName();
            sb.append("- ").append(nombre).append("\n");
        }
        sb.append("\n¿Quieres cerrarlos antes de salir?");

        Object[] opciones = {"Cerrar servidores y salir", "Cancelar"};
        int res = JOptionPane.showOptionDialog(
                this,
                sb.toString(),
                "Servidores activos",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                opciones,
                opciones[0]
        );
        if(res != 0) return;

        this.setEnabled(false);
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        new Thread(() -> {
            try{
                gestorServidores.detenerServidoresActivosParaSalir();
            } finally{
                SwingUtilities.invokeLater(() -> {
                    try {
                        VentanaPrincipal.this.dispose();
                    } finally {
                        System.exit(0);
                    }
                });
            }
        }, "shutdown-servidores").start();
    }

    private PanelServidores getPanelServidores(GestorServidores gestorServidores) {
        PanelServidores listaServidoresPanel = new PanelServidores(gestorServidores);
        listaServidoresPanel.setServidorSeleccionadoListener(server -> aplicarCambioServidor(server, true));
        listaServidoresPanel.setServidorContextMenuListener(new PanelServidores.ServidorContextMenuListener() {
            @Override
            public void abrirConfiguracion(Server server) {
                abrirPaginaServidor(server, PaginaDerecha.CONFIG);
            }

            @Override
            public void abrirMundos(Server server) {
                abrirPaginaServidor(server, PaginaDerecha.MUNDO);
            }
        });
        return listaServidoresPanel;
    }

    private void abrirPaginaServidor(Server server, PaginaDerecha pagina){
        if(server == null || pagina == null) return;

        Server serverSeleccionado = gestorServidores.getServidorSeleccionado();
        boolean mismoServidor = esMismoServidor(serverSeleccionado, server);

        if(!mismoServidor){
            if(!aplicarCambioServidor(server, false)) return;
            listaServidoresPanel.mostrarSeleccionServidor(server);
        } else {
            if(!confirmarSalidaConfiguracion(server, pagina)) return;
            gestorServidores.setServidorSeleccionado(server);
            if(abrirCarpetaServerButton != null) abrirCarpetaServerButton.setEnabled(true);
            if(borrarServerButton != null) borrarServerButton.setEnabled(true);
            boolean panelDesincronizado = !esMismoServidor(serverMostrado, server);
            if(panelDerecho != null && panelDesincronizado) {
                mostrarPanelDerecho(server, gestorServidores);
            }
        }

        setPaginaDerecha(pagina);
    }

    private void navegarAPaginaDerecha(PaginaDerecha pagina){
        if(pagina == null) pagina = PaginaDerecha.HOME;
        if(pagina == paginaDerechaActual){
            setPaginaDerecha(pagina);
            return;
        }
        if(!confirmarSalidaConfiguracion(gestorServidores.getServidorSeleccionado(), pagina)) return;
        setPaginaDerecha(pagina);
    }

    private boolean aplicarCambioServidor(Server server, boolean restaurarSeleccionSiCancela){
        if(server == null) return false;

        Server servidorActual = gestorServidores.getServidorSeleccionado();
        if(!confirmarSalidaConfiguracion(server, paginaDerechaActual)){
            if(restaurarSeleccionSiCancela && servidorActual != null){
                listaServidoresPanel.mostrarSeleccionServidor(servidorActual);
            }
            return false;
        }

        if(esMismoServidor(servidorActual, server)){
            return true;
        }

        gestorServidores.setServidorSeleccionado(server);
        System.out.println("Servidor seleccionado: " + server.getServerDir());
        if(abrirCarpetaServerButton != null) abrirCarpetaServerButton.setEnabled(true);
        if(borrarServerButton != null) borrarServerButton.setEnabled(true);
        if(panelDerecho != null) {
            mostrarPanelDerecho(server, gestorServidores);
        }
        return true;
    }

    private boolean confirmarSalidaConfiguracion(Server serverDestino, PaginaDerecha paginaDestino){
        if(paginaDerechaActual != PaginaDerecha.CONFIG || panelConfigServidor == null){
            return true;
        }

        Server servidorActual = gestorServidores.getServidorSeleccionado();
        PaginaDerecha destino = paginaDestino != null ? paginaDestino : paginaDerechaActual;
        boolean mismoServidor = esMismoServidor(servidorActual, serverDestino);
        boolean permaneceEnConfiguracion = destino == PaginaDerecha.CONFIG && (serverDestino == null || mismoServidor);
        if(permaneceEnConfiguracion){
            return true;
        }

        return panelConfigServidor.confirmDiscardOrSave(this);
    }

    private boolean esMismoServidor(Server a, Server b){
        if(a == null || b == null) return false;

        String idA = a.getId();
        String idB = b.getId();
        if(idA != null && idB != null){
            return idA.equals(idB);
        }

        String dirA = a.getServerDir();
        String dirB = b.getServerDir();
        if(dirA == null || dirB == null) return false;
        return dirA.equals(dirB);
    }

    private void estilizarBoton(JButton button){
        Font base = button.getFont();
        button.setFont(base.deriveFont(Font.BOLD, Math.max(14f, base.getSize2D() + 2f)));
        button.setMargin(new Insets(10, 14, 10, 14));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBackground(AppTheme.getBackground());
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if(!button.isEnabled()) return;
                button.setBackground(colorSeleccionPanelServidores());
                button.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(AppTheme.getBackground());
                button.repaint();
            }
        });
    }
}

