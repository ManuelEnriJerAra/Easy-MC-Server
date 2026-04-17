package vista;

import com.formdev.flatlaf.extras.components.FlatButton;
import com.formdev.flatlaf.extras.components.FlatScrollPane;
import controlador.GestorServidores;
import controlador.MojangAPI;
import controlador.Utilidades;
import modelo.Server;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.swing.*;
import java.io.IOException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.datatransfer.StringSelection;
import java.beans.PropertyChangeListener;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PanelJugadores extends JPanel {
    private static final Pattern JOIN = Pattern.compile("([^\\s]+) joined the game");
    private static final Pattern LEFT = Pattern.compile("([^\\s]+) left the game");

    private final GestorServidores gestorServidores;
    private final MojangAPI mojangAPI = new MojangAPI();
    private final ObjectMapper mapper = new ObjectMapper();
    private static final Map<String, ImageIcon> CACHE_CABEZAS = new ConcurrentHashMap<>();
    private static final Set<String> CARGANDO = ConcurrentHashMap.newKeySet();

    private static final String CARD_JUGADORES = "jugadores";
    private static final String CARD_VACIO = "vacio";

    private final JPanel contenedorJugadores = new JPanel(new WrapLayout(FlowLayout.LEFT, 10, 8));
    private final Map<String, PlayerPanel> panelsPorJugador = new LinkedHashMap<>();
    private final JScrollPane scrollJugadores;
    private final JPanel panelCentro = new JPanel(new CardLayout());
    private final JPanel panelSinJugadores = new JPanel(new GridBagLayout());

    private final JButton btnWhitelist = new FlatButton();
    private final JButton btnOps = new FlatButton();
    private final JButton btnBaneados = new FlatButton();
    private final JButton btnBaneadosIp = new FlatButton();

    private Server server;
    private Consumer<String> consoleListener;
    private final PropertyChangeListener listenerEstadoServidor;
    private final int maxPlayers;

    public PanelJugadores(GestorServidores gestorServidores) {
        this(gestorServidores, true);
    }

    public PanelJugadores(GestorServidores gestorServidores, boolean showTitle) {
        this.gestorServidores = gestorServidores;
        this.setLayout(new BorderLayout());
        this.setOpaque(true);
        this.setBackground(AppTheme.getPanelBackground());

        if(showTitle){
            JLabel titulo = new JLabel("Jugadores");
            AppTheme.applyCardTitleStyle(titulo);
            this.add(titulo, BorderLayout.NORTH);
        }

        contenedorJugadores.setOpaque(true);
        contenedorJugadores.setBackground(AppTheme.getPanelBackground());
        scrollJugadores = new FlatScrollPane();
        scrollJugadores.setViewportView(contenedorJugadores);
        scrollJugadores.setBorder(null);
        scrollJugadores.setOpaque(true);
        scrollJugadores.setBackground(AppTheme.getPanelBackground());
        scrollJugadores.getViewport().setOpaque(true);
        scrollJugadores.getViewport().setBackground(AppTheme.getPanelBackground());
        // Queremos que el panel ocupe el espacio y se adapte al ancho (varias filas). Si no cabe: scroll vertical.
        scrollJugadores.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollJugadores.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        JLabel labelSinJugadores = new JLabel("No hay ningún jugador conectado.");
        labelSinJugadores.setFont(labelSinJugadores.getFont().deriveFont(Font.PLAIN, 15f));
        labelSinJugadores.setForeground(AppTheme.getMutedForeground());
        panelSinJugadores.setOpaque(true);
        panelSinJugadores.setBackground(AppTheme.getPanelBackground());
        panelSinJugadores.add(labelSinJugadores);

        panelCentro.setOpaque(true);
        panelCentro.setBackground(AppTheme.getPanelBackground());
        panelCentro.add(scrollJugadores, CARD_JUGADORES);
        panelCentro.add(panelSinJugadores, CARD_VACIO);

        this.add(panelCentro, BorderLayout.CENTER);
        aplicarEstiloScrollJugadores();

        configurarBotonLista(btnWhitelist, "Whitelist", () -> abrirDialogoLista(
                "Whitelist",
                TipoLista.WHITELIST
        ));
        configurarBotonLista(btnOps, "OPs", () -> abrirDialogoLista(
                "OPs",
                TipoLista.OPS
        ));
        configurarBotonLista(btnBaneados, "Baneados", () -> abrirDialogoLista(
                "Baneados",
                TipoLista.BANNED_PLAYERS
        ));
        configurarBotonLista(btnBaneadosIp, "Baneados IP", () -> abrirDialogoLista(
                "Baneados IP",
                TipoLista.BANNED_IPS
        ));

        // Barra inferior integrada en el borde inferior del panel
        JPanel barraInferior = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        barraInferior.setOpaque(true);
        barraInferior.setBackground(AppTheme.getPanelBackground());
        barraInferior.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        barraInferior.add(btnWhitelist);
        barraInferior.add(btnOps);
        barraInferior.add(btnBaneados);
        barraInferior.add(btnBaneadosIp);

        this.add(barraInferior, BorderLayout.SOUTH);

        server = gestorServidores.getServidorSeleccionado();
        maxPlayers = leerMaxPlayers(server);
        if (server != null) {
            // Estado inicial basado en logs existentes
            recomputarDesdeLogs(server.getRawLogLines());

            consoleListener = this::procesarLinea;
            server.addConsoleListener(consoleListener);
        }

        listenerEstadoServidor = evt -> {
            if(!"estadoServidor".equals(evt.getPropertyName())) return;
            Object v = evt.getNewValue();
            if(!(v instanceof Server s)) return;
            if(server == null) return;
            if(s.getId() == null || server.getId() == null) return;
            if(!s.getId().equals(server.getId())) return;

            boolean vivo = s.getServerProcess() != null && s.getServerProcess().isAlive();
            if(!vivo){
                SwingUtilities.invokeLater(() -> setJugadores(Set.of()));
            }
        };
        gestorServidores.addPropertyChangeListener("estadoServidor", listenerEstadoServidor);

        recargarContadores();

        // Limpieza de listener
        this.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) == 0) return;
            if (!isDisplayable()) {
                if (server != null && consoleListener != null) {
                    server.removeConsoleListener(consoleListener);
                }
                gestorServidores.removePropertyChangeListener("estadoServidor", listenerEstadoServidor);
            }
        });
    }

    public void refrescarPanel() {
        recargarContadores();
        if (server != null) {
            recomputarDesdeLogs(server.getRawLogLines());
        } else {
            setJugadores(Set.of());
        }
    }

    @Override
    public void updateUI(){
        super.updateUI();
        aplicarEstiloScrollJugadores();
    }

    private void aplicarEstiloScrollJugadores(){
        if(scrollJugadores == null) return;
        Color panelBg = AppTheme.getPanelBackground();
        setBackground(panelBg);
        contenedorJugadores.setOpaque(true);
        contenedorJugadores.setBackground(panelBg);
        panelCentro.setOpaque(true);
        panelCentro.setBackground(panelBg);
        panelSinJugadores.setOpaque(true);
        panelSinJugadores.setBackground(panelBg);
        scrollJugadores.setBorder(null);
        scrollJugadores.setViewportBorder(null);
        scrollJugadores.setOpaque(true);
        scrollJugadores.setBackground(panelBg);
        if(scrollJugadores.getViewport() != null){
            scrollJugadores.getViewport().setOpaque(true);
            scrollJugadores.getViewport().setBackground(panelBg);
        }
    }

    private enum TipoLista {
        WHITELIST("whitelist.json"),
        OPS("ops.json"),
        BANNED_PLAYERS("banned-players.json"),
        BANNED_IPS("banned-ips.json");

        final String filename;
        TipoLista(String filename) {
            this.filename = filename;
        }
    }

    private void configurarBotonLista(JButton btn, String titulo, Runnable onClick){
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setToolTipText("Abrir " + titulo);
        btn.putClientProperty("baseText", titulo);
        btn.setText(titulo);
        estilizarBotonBarra(btn);
        btn.addActionListener(e -> onClick.run());
    }

    private void estilizarBotonBarra(JButton btn){
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setContentAreaFilled(true);
        btn.setBackground(AppTheme.getBackground());

        Font base = btn.getFont();
        btn.setFont(base.deriveFont(Font.BOLD, Math.max(12f, base.getSize2D())));
        btn.setMargin(new Insets(6, 10, 6, 10));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if(!btn.isEnabled()) return;
                btn.setBackground(AppTheme.getSelectionBackground());
                btn.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(AppTheme.getBackground());
                btn.repaint();
            }
        });
    }

    private void procesarLinea(String raw) {
        if (raw == null || raw.isBlank()) return;

        Matcher mJoin = JOIN.matcher(raw);
        if (mJoin.find()) {
            String name = mJoin.group(1);
            if (name != null && !name.isBlank()) {
                SwingUtilities.invokeLater(() -> addJugador(name.strip()));
            }
            SwingUtilities.invokeLater(this::recargarContadores);
            return;
        }

        Matcher mLeft = LEFT.matcher(raw);
        if (mLeft.find()) {
            String name = mLeft.group(1);
            if (name != null && !name.isBlank()) {
                SwingUtilities.invokeLater(() -> removeJugador(name.strip()));
            }
            SwingUtilities.invokeLater(this::recargarContadores);
        }
    }

    private void recomputarDesdeLogs(List<String> rawLogLines) {
        if (rawLogLines == null || rawLogLines.isEmpty()) {
            setJugadores(Set.of());
            return;
        }

        LinkedHashSet<String> actuales = new LinkedHashSet<>();
        for (String raw : rawLogLines) {
            if (raw == null) continue;

            Matcher mJoin = JOIN.matcher(raw);
            if (mJoin.find()) {
                String name = mJoin.group(1);
                if (name != null && !name.isBlank()) {
                    addToSetWithLimit(actuales, name.strip());
                }
                continue;
            }

            Matcher mLeft = LEFT.matcher(raw);
            if (mLeft.find()) {
                String name = mLeft.group(1);
                if (name != null && !name.isBlank()) actuales.remove(name.strip());
            }
        }

        setJugadores(actuales);
    }

    private void setJugadores(Set<String> nombres) {
        LinkedHashSet<String> target = new LinkedHashSet<>();
        if(nombres != null){
            for(String n : nombres){
                if(n == null || n.isBlank()) continue;
                addToSetWithLimit(target, n);
            }
        }

        // Eliminar los que ya no están
        Iterator<Map.Entry<String, PlayerPanel>> it = panelsPorJugador.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PlayerPanel> entry = it.next();
            if (target.contains(entry.getKey())) continue;
            contenedorJugadores.remove(entry.getValue());
            it.remove();
        }

        // Añadir nuevos manteniendo orden
        for (String name : target) {
            if (name == null || name.isBlank()) continue;
            if (panelsPorJugador.containsKey(name)) continue;
            PlayerPanel p = new PlayerPanel(name);
            panelsPorJugador.put(name, p);
            contenedorJugadores.add(p);
        }

        refrescarUI();
    }

    private void addJugador(String nombre) {
        if (nombre == null || nombre.isBlank()) return;
        if (panelsPorJugador.size() >= maxPlayers) return;
        if (panelsPorJugador.containsKey(nombre)) return;
        PlayerPanel p = new PlayerPanel(nombre);
        panelsPorJugador.put(nombre, p);
        contenedorJugadores.add(p);
        refrescarUI();
    }

    private void removeJugador(String nombre) {
        if (nombre == null || nombre.isBlank()) return;
        PlayerPanel p = panelsPorJugador.remove(nombre);
        if (p == null) return;
        contenedorJugadores.remove(p);
        refrescarUI();
    }

    private void enviarComando(String comando){
        Server s = this.server;
        if(s == null) return;
        // Evita ensuciar el log con acciones del panel
        gestorServidores.mandarComando(s, comando, false);
    }

    private void refrescarUI(){
        CardLayout cardLayout = (CardLayout) panelCentro.getLayout();
        cardLayout.show(panelCentro, panelsPorJugador.isEmpty() ? CARD_VACIO : CARD_JUGADORES);
        panelCentro.revalidate();
        panelCentro.repaint();
    }

    private void recargarContadores(){
        btnWhitelist.setText(formatearTextoConConteo(btnWhitelist, cargarListaDesdeArchivo(TipoLista.WHITELIST).size()));
        btnOps.setText(formatearTextoConConteo(btnOps, cargarListaDesdeArchivo(TipoLista.OPS).size()));
        btnBaneados.setText(formatearTextoConConteo(btnBaneados, cargarListaDesdeArchivo(TipoLista.BANNED_PLAYERS).size()));
        btnBaneadosIp.setText(formatearTextoConConteo(btnBaneadosIp, cargarListaDesdeArchivo(TipoLista.BANNED_IPS).size()));
        this.revalidate();
        this.repaint();
    }

    private String formatearTextoConConteo(JButton btn, int count){
        Object baseObj = btn.getClientProperty("baseText");
        String base = (baseObj == null) ? btn.getText() : String.valueOf(baseObj);
        return base + " (" + count + ")";
    }

    private List<String> cargarListaDesdeArchivo(TipoLista tipo){
        Server s = this.server;
        if(s == null) return List.of();
        String dir = s.getServerDir();
        if(dir == null || dir.isBlank()) return List.of();

        Path file = Path.of(dir).resolve(tipo.filename);
        if(!Files.exists(file)) return List.of();

        try(InputStream in = Files.newInputStream(file)){
            JsonNode root = mapper.readTree(in);
            if(root == null || !root.isArray()) return List.of();
            List<String> out = new ArrayList<>();
            for(JsonNode n : root){
                if(n == null) continue;
                String v;
                if(tipo == TipoLista.BANNED_IPS){
                    v = n.path("ip").asString();
                } else {
                    v = n.path("name").asString();
                }
                if(v == null) continue;
                v = v.strip();
                if(v.isBlank()) continue;
                out.add(v);
            }
            out.sort(String.CASE_INSENSITIVE_ORDER);
            return out;
        } catch (Exception e){
            return List.of();
        }
    }

    private void abrirDialogoLista(String titulo, TipoLista tipo){
        List<String> inicial = cargarListaDesdeArchivo(tipo);

        DefaultListModel<String> model = new DefaultListModel<>();
        for(String s : inicial) model.addElement(s);
        JList<String> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        FlatScrollPane scroll = new FlatScrollPane();
        scroll.setViewportView(list);

        JButton btnAdd = new FlatButton();
        btnAdd.setText("Añadir");
        JButton btnRemove = new FlatButton();
        btnRemove.setText("Quitar");
        JButton btnRefresh = new FlatButton();
        btnRefresh.setText("Recargar");
        JButton btnClose = new FlatButton();
        btnClose.setText("Cerrar");

        btnAdd.addActionListener(e -> onAdd(tipo, model));
        btnRemove.addActionListener(e -> onRemove(tipo, list, model));
        btnRefresh.addActionListener(e -> recargarModel(tipo, model));
        btnClose.addActionListener(e -> SwingUtilities.getWindowAncestor(btnClose).dispose());

        boolean serverActivo = server != null && server.getServerProcess() != null && server.getServerProcess().isAlive();
        btnAdd.setEnabled(serverActivo);
        btnRemove.setEnabled(serverActivo);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        botones.add(btnAdd);
        botones.add(btnRemove);
        botones.add(btnRefresh);
        botones.add(btnClose);

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(botones, BorderLayout.SOUTH);

        if(!serverActivo){
            JLabel aviso = new JLabel("Inicia el servidor para modificar esta lista (solo lectura).");
            aviso.setForeground(AppTheme.getMutedForeground());
            panel.add(aviso, BorderLayout.NORTH);
        }

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), titulo, true);
        dialog.setContentPane(panel);
        dialog.setSize(420, 420);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);

        recargarContadores();
    }

    private void recargarModel(TipoLista tipo, DefaultListModel<String> model){
        List<String> data = cargarListaDesdeArchivo(tipo);
        model.clear();
        for(String s : data) model.addElement(s);
        recargarContadores();
    }

    private void onAdd(TipoLista tipo, DefaultListModel<String> model){
        String prompt = switch (tipo){
            case BANNED_IPS -> "IP:";
            default -> "Nombre de jugador:";
        };
        String input = JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this),
                prompt,
                "Añadir a " + tipo.name(),
                JOptionPane.QUESTION_MESSAGE
        );
        if(input == null) return;
        input = input.strip();
        if(input.isBlank()) return;

        String cmd = comandoAdd(tipo, input);
        if(cmd == null) return;
        enviarComando(cmd);

        // Recargar tras un breve momento para dar tiempo a que el server escriba el fichero
        new javax.swing.Timer(500, (ActionEvent e) -> {
            ((javax.swing.Timer) e.getSource()).stop();
            recargarModel(tipo, model);
        }).start();
    }

    private void onRemove(TipoLista tipo, JList<String> list, DefaultListModel<String> model){
        String selected = list.getSelectedValue();
        if(selected == null || selected.isBlank()) return;

        int res = JOptionPane.showConfirmDialog(
                SwingUtilities.getWindowAncestor(this),
                "¿Quitar '" + selected + "'?",
                "Confirmar",
                JOptionPane.YES_NO_OPTION
        );
        if(res != JOptionPane.YES_OPTION) return;

        String cmd = comandoRemove(tipo, selected);
        if(cmd == null) return;
        enviarComando(cmd);

        new javax.swing.Timer(500, (ActionEvent e) -> {
            ((javax.swing.Timer) e.getSource()).stop();
            recargarModel(tipo, model);
        }).start();
    }

    private String comandoAdd(TipoLista tipo, String value){
        return switch (tipo){
            case WHITELIST -> "whitelist add " + value;
            case OPS -> "op " + value;
            case BANNED_PLAYERS -> "ban " + value;
            case BANNED_IPS -> "ban-ip " + value;
        };
    }

    private String comandoRemove(TipoLista tipo, String value){
        return switch (tipo){
            case WHITELIST -> "whitelist remove " + value;
            case OPS -> "deop " + value;
            case BANNED_PLAYERS -> "pardon " + value;
            case BANNED_IPS -> "pardon-ip " + value;
        };
    }

    private void addToSetWithLimit(LinkedHashSet<String> set, String nombre){
        if(nombre == null || nombre.isBlank()) return;
        if(set.contains(nombre)) return;
        if(set.size() >= maxPlayers) return;
        set.add(nombre);
    }

    private int leerMaxPlayers(Server server){
        try{
            if(server == null) return 20;
            String dir = server.getServerDir();
            if(dir == null || dir.isBlank()) return 20;

            Path p = Path.of(dir).resolve("server.properties");
            if(!Files.exists(p)) return 20;

            Properties props = Utilidades.cargarPropertiesUtf8(p);

            String s = props.getProperty("max-players");
            if(s == null || s.isBlank()) return 20;
            int n = Integer.parseInt(s.trim());
            return (n <= 0) ? 20 : n;
        } catch (IOException | NumberFormatException e){
            return 20;
        }
    }

    private class PlayerPanel extends JPanel {
        private final JLabel icon = new JLabel();
        private final JLabel nameLabel;
        private final Color bgNormal;
        private final Color bgHover;
        private final javax.swing.border.Border borderNormal;
        private final javax.swing.border.Border borderHover;

        PlayerPanel(String username) {
            super(new BorderLayout(8, 0));
            this.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 10));
            this.setToolTipText("Click derecho para acciones");
            this.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            bgNormal = AppTheme.getPanelBackground();
            bgHover = AppTheme.getSoftSelectionBackground();
            borderNormal = this.getBorder();
            borderHover = AppTheme.createAccentBorder(new Insets(5, 7, 5, 9), 1f);

            icon.setPreferredSize(new Dimension(32, 32));
            icon.setMinimumSize(new Dimension(32, 32));
            this.add(icon, BorderLayout.WEST);

            nameLabel = new JLabel(username);
            this.add(nameLabel, BorderLayout.CENTER);

            cargarCabezaAsync(username);
            instalarMenuContextual(username);
        }

        private void instalarMenuContextual(String username){
            JPopupMenu menu = crearMenuJugador(username);

            MouseAdapter adapter = new MouseAdapter() {
                private void maybeShow(MouseEvent e){
                    if(!e.isPopupTrigger()) return;
                    menu.show(e.getComponent(), e.getX(), e.getY());
                }

                @Override public void mousePressed(MouseEvent e){ maybeShow(e); }
                @Override public void mouseReleased(MouseEvent e){ maybeShow(e); }
                @Override public void mouseEntered(MouseEvent e){ setHover(true); }
                @Override public void mouseExited(MouseEvent e){ setHover(false); }
            };

            this.addMouseListener(adapter);
            icon.addMouseListener(adapter);
            nameLabel.addMouseListener(adapter);
        }

        private void setHover(boolean hover){
            if(hover){
                setOpaque(true);
                setBackground(bgHover);
                setBorder(borderHover);
            } else {
                setOpaque(false);
                setBackground(bgNormal);
                setBorder(borderNormal);
            }
            revalidate();
            repaint();
        }

        private JPopupMenu crearMenuJugador(String username){
            JPopupMenu menu = new JPopupMenu();

            JMenuItem copiar = new JMenuItem("Copiar nombre");
            copiar.addActionListener(e -> {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(username), null);
            });
            menu.add(copiar);
            menu.addSeparator();

            JMenuItem op = new JMenuItem("OP");
            op.addActionListener(e -> enviarComando("op " + username));
            menu.add(op);

            JMenuItem deop = new JMenuItem("De-OP");
            deop.addActionListener(e -> enviarComando("deop " + username));
            menu.add(deop);

            menu.addSeparator();

            JMenuItem kick = new JMenuItem("Kick...");
            kick.addActionListener(e -> {
                String razon = JOptionPane.showInputDialog(
                        SwingUtilities.getWindowAncestor(PanelJugadores.this),
                        "Razón (opcional):",
                        "Kick a " + username,
                        JOptionPane.QUESTION_MESSAGE
                );
                if(razon == null) return;
                razon = razon.strip();
                enviarComando(razon.isBlank() ? ("kick " + username) : ("kick " + username + " " + razon));
            });
            menu.add(kick);

            JMenuItem ban = new JMenuItem("Ban...");
            ban.addActionListener(e -> {
                String razon = JOptionPane.showInputDialog(
                        SwingUtilities.getWindowAncestor(PanelJugadores.this),
                        "Razón (opcional):",
                        "Ban a " + username,
                        JOptionPane.QUESTION_MESSAGE
                );
                if(razon == null) return;
                razon = razon.strip();
                enviarComando(razon.isBlank() ? ("ban " + username) : ("ban " + username + " " + razon));
            });
            menu.add(ban);

            JMenuItem pardon = new JMenuItem("Pardon (unban)");
            pardon.addActionListener(e -> enviarComando("pardon " + username));
            menu.add(pardon);

            menu.addSeparator();

            JMenuItem wlAdd = new JMenuItem("Whitelist add");
            wlAdd.addActionListener(e -> enviarComando("whitelist add " + username));
            menu.add(wlAdd);

            JMenuItem wlRemove = new JMenuItem("Whitelist remove");
            wlRemove.addActionListener(e -> enviarComando("whitelist remove " + username));
            menu.add(wlRemove);

            menu.addSeparator();

            JMenuItem tell = new JMenuItem("Enviar mensaje (/tell)...");
            tell.addActionListener(e -> {
                String msg = JOptionPane.showInputDialog(
                        SwingUtilities.getWindowAncestor(PanelJugadores.this),
                        "Mensaje:",
                        "Mensaje a " + username,
                        JOptionPane.QUESTION_MESSAGE
                );
                if(msg == null) return;
                msg = msg.strip();
                if(msg.isBlank()) return;
                enviarComando("tell " + username + " " + msg);
            });
            menu.add(tell);

            return menu;
        }

        private void cargarCabezaAsync(String username) {
            String key = (username == null) ? "" : username.strip().toLowerCase(Locale.ROOT);
            if(key.isBlank()) return;

            ImageIcon cached = CACHE_CABEZAS.get(key);
            if (cached != null) {
                icon.setIcon(cached);
                return;
            }

            if(!CARGANDO.add(key)){
                return; // ya hay una descarga en curso para este jugador
            }

            MojangAPI.runBackgroundRequest(() -> {
                try{
                    ImageIcon head = mojangAPI.obtenerCabezaJugador(username, 32);
                    if (head == null) return;
                    CACHE_CABEZAS.put(key, head);
                    SwingUtilities.invokeLater(() -> {
                        icon.setIcon(head);
                        icon.revalidate();
                        icon.repaint();
                    });
                } finally{
                    CARGANDO.remove(key);
                }
            });
        }
    }

}
