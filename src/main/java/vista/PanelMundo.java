package vista;

import controlador.GestorMundos;
import controlador.GestorServidores;
import controlador.MCARenderer;
import controlador.WorldDataReader;
import modelo.MinecraftConstants;
import modelo.Server;
import modelo.World;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class PanelMundo extends JPanel {
    private static final Pattern JOIN = Pattern.compile("([^\\s]+) joined the game");
    private static final Pattern HORA_LOG = Pattern.compile("^\\[(\\d{2}:\\d{2}:\\d{2})]\\s*");
    private static final DateTimeFormatter FORMATO_HORA_LOG = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATO_CONEXION = DateTimeFormatter.ofPattern("dd/MM/yyyy - HH:mm:ss");
    private static final String WORLD_METADATA_FILE = ".emw-world.properties";
    private static final Map<Path, RegionVisibilityCacheEntry> REGION_VISIBILITY_CACHE = new ConcurrentHashMap<>();

    private final GestorServidores gestorServidores;
    private final Runnable onWorldChanged;

    private final JLabel tiempoRealLabel = new JLabel("-");
    private final JLabel lastPlayedLabel = new JLabel("Última apertura: -");
    private final JLabel versionLabel = new JLabel("Versión: -");
    private final JLabel tipoMundoLabel = new JLabel("-");
    private final PreviewImagePanel previewImageLabel = new PreviewImagePanel();
    private final PreviewSpinner previewSpinner = new PreviewSpinner();
    private final JLabel seedValueLabel = new JLabel("-");
    private final JLabel tiempoRealValueLabel = new JLabel("-");
    private final JLabel lastPlayedValueLabel = new JLabel("-");
    private final JLabel versionValueLabel = new JLabel("-");
    private final JLabel tipoMundoValueLabel = new JLabel("-");
    private final JComboBox<World> mundosCombo = new JComboBox<>();
    private final JButton refrescarButton = new JButton("Refrescar");
    private final JButton usarEsteMundoButton = new JButton("Usar este mundo");
    private final JButton importarButton = new JButton("Importar mundo");
    private final JButton exportarButton = new JButton("Exportar mundo");
    private final JButton generarButton = new JButton("Generar nuevo");
    private final JButton generarPreviewButton = new JButton("Generar preview");
    private final JButton previewMenuButton = new JButton("\u2630");
    private final JPopupMenu previewOptionsMenu = new JPopupMenu();
    private final JCheckBox sombreadoMenuItem = new JCheckBox("Sombreado", true);
    private final JCheckBox mostrarSpawnMenuItem = new JCheckBox("Mostrar spawn", false);
    private final JCheckBox usarTodoMenuItem = new JCheckBox("Usar todo", false);
    private final JLabel pesoMundoLabel = new JLabel("-");
    private final JLabel pesoStatsSavesLabel = new JLabel("Peso stats y saves: -");
    private final JLabel pesoTotalLabel = new JLabel("-");
    private final JLabel pesoMundoValueLabel = new JLabel("-");
    private final JLabel pesoStatsSavesValueLabel = new JLabel("-");
    private final JLabel pesoTotalValueLabel = new JLabel("-");
    private final JPanel conexionesPanel = new JPanel();

    private World mundoActivoActual;
    private boolean actualizandoComboMundos = false;
    private boolean sombreadoEnPreview = true;
    private boolean mostrarSpawnEnPreview = false;
    private boolean usarTodoEnPreview = false;
    private boolean previewGenerationInProgress = false;

    PanelMundo(GestorServidores gestorServidores, Runnable onWorldChanged) {
        this.gestorServidores = gestorServidores;
        this.onWorldChanged = onWorldChanged;
        setLayout(new BorderLayout());
        setOpaque(false);

        styleActionButton(refrescarButton);
        styleActionButton(importarButton);
        styleActionButton(exportarButton);
        styleActionButton(generarButton);
        styleActionButton(generarPreviewButton);
        stylePreviewOverlayButton(generarPreviewButton);
        stylePreviewMenuButton(previewMenuButton);
        applyDefaultPrimaryButtonStyle();

        previewImageLabel.setPreferredSize(new Dimension(320, 320));
        previewImageLabel.setMinimumSize(new Dimension(260, 260));
        instalarMenuContextualPreview();
        previewSpinner.setVisible(false);

        styleInfoLabel(seedValueLabel);
        styleInfoLabel(tiempoRealValueLabel);
        styleInfoLabel(lastPlayedValueLabel);
        styleInfoLabel(versionValueLabel);
        styleInfoLabel(tipoMundoValueLabel);
        styleInfoLabel(pesoMundoValueLabel);
        styleInfoLabel(pesoStatsSavesValueLabel);
        styleInfoLabel(pesoTotalValueLabel);
        instalarInteraccionSeed();

        conexionesPanel.setOpaque(false);
        conexionesPanel.setLayout(new BoxLayout(conexionesPanel, BoxLayout.Y_AXIS));

        mundosCombo.addActionListener(e -> {
            if (actualizandoComboMundos) return;
            actualizarVistaMundos();
        });
        refrescarButton.addActionListener(e -> refrescarDatos());
        usarEsteMundoButton.addActionListener(e -> cambiarMundoSeleccionado());
        importarButton.addActionListener(e -> {
            if (GestorMundos.importarMundo(gestorServidores.getServidorSeleccionado(), this)) {
                refrescarDatos();
                notificarCambioDeMundo();
            }
        });
        exportarButton.addActionListener(e -> {
            World mundo = (World) mundosCombo.getSelectedItem();
            if (GestorMundos.exportarMundo(gestorServidores.getServidorSeleccionado(), mundo, this)) {
                refrescarDatos();
            }
        });
        generarButton.addActionListener(e -> abrirDialogoNuevoMundo());
        generarPreviewButton.addActionListener(e -> generarPreviewMundoSeleccionado());
        instalarMenuOpcionesPreview();

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder());
        body.add(crearTarjetaPrincipal());
        body.add(Box.createVerticalStrut(8));
        body.add(crearTarjetasInferiores());

        add(body, BorderLayout.CENTER);

        refrescarDatos();
    }

    private JPanel crearTarjetaPrincipal() {
        CardPanel card = new CardPanel(new BorderLayout(12, 0), new Insets(12, 12, 12, 12));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel izquierda = new JPanel();
        izquierda.setOpaque(false);
        izquierda.setLayout(new BoxLayout(izquierda, BoxLayout.Y_AXIS));

        JPanel izquierdaWrap = new JPanel(new BorderLayout());
        izquierdaWrap.setOpaque(false);

        JLabel titulo = crearTituloTarjeta("Mundo");

        JPanel selectorPanel = new JPanel(new BorderLayout(8, 0));
        selectorPanel.setOpaque(false);
        selectorPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mundosCombo.setPreferredSize(new Dimension(250, 36));
        mundosCombo.setFont(mundosCombo.getFont().deriveFont(Font.BOLD, Math.max(13f, mundosCombo.getFont().getSize2D())));
        mundosCombo.putClientProperty("JComponent.roundRect", true);
        selectorPanel.add(mundosCombo, BorderLayout.CENTER);
        selectorPanel.add(usarEsteMundoButton, BorderLayout.EAST);

        JPanel infoPanel = new JPanel();
        infoPanel.setOpaque(false);
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(crearInfoRow("Tiempo activo:", tiempoRealValueLabel));
        infoPanel.add(Box.createVerticalStrut(2));
        infoPanel.add(crearInfoRow("Última apertura:", lastPlayedValueLabel));
        infoPanel.add(Box.createVerticalStrut(2));
        infoPanel.add(crearInfoRow("Versión:", versionValueLabel));
        infoPanel.add(Box.createVerticalStrut(2));
        infoPanel.add(crearSeedRow());
        infoPanel.add(Box.createVerticalStrut(2));
        infoPanel.add(crearInfoRow("Tipo:", tipoMundoValueLabel));

        JPanel accionesPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 2));
        accionesPanel.setOpaque(false);
        accionesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        accionesPanel.add(refrescarButton);
        accionesPanel.add(generarButton);
        accionesPanel.add(importarButton);
        accionesPanel.add(exportarButton);

        izquierda.add(titulo);
        izquierda.add(Box.createVerticalStrut(6));
        izquierda.add(selectorPanel);
        izquierda.add(Box.createVerticalStrut(6));
        izquierda.add(infoPanel);
        izquierda.add(Box.createVerticalStrut(6));
        izquierda.add(accionesPanel);

        izquierdaWrap.add(izquierda, BorderLayout.NORTH);
        card.add(izquierdaWrap, BorderLayout.CENTER);

        JPanel previewWrap = new JPanel(new BorderLayout());
        previewWrap.setOpaque(false);
        previewWrap.add(crearPanelPreview(), BorderLayout.NORTH);
        card.add(previewWrap, BorderLayout.EAST);
        return card;
    }

    private JPanel crearSeedRow() {
        return crearInfoRow("Seed:", seedValueLabel);
    }

    private JPanel crearInfoRow(String titulo, JLabel valorLabel) {
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel tituloLabel = new JLabel(titulo);
        tituloLabel.setFont(tituloLabel.getFont().deriveFont(Font.BOLD));
        tituloLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        valorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(tituloLabel, BorderLayout.WEST);
        panel.add(valorLabel, BorderLayout.CENTER);
        return panel;
    }

    private JLabel crearTituloTarjeta(String texto) {
        JLabel titulo = new JLabel(texto);
        AppTheme.applyCardTitleStyle(titulo);
        titulo.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        return titulo;
    }

    private JPanel crearTarjetasInferiores() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 12, 0));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(crearTarjetaDatos());
        panel.add(crearTarjetaConexiones());
        return panel;
    }

    private JPanel crearTarjetaDatos() {
        CardPanel card = new CardPanel(new BorderLayout(), new Insets(12, 12, 12, 12));

        JLabel titulo = crearTituloTarjeta("Datos");
        card.add(titulo, BorderLayout.NORTH);

        JPanel contenido = new JPanel();
        contenido.setOpaque(false);
        contenido.setLayout(new BoxLayout(contenido, BoxLayout.Y_AXIS));
        contenido.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        contenido.add(crearInfoRow("Peso mundo:", pesoMundoValueLabel));
        contenido.add(Box.createVerticalStrut(2));
        contenido.add(crearInfoRow("Peso stats y saves:", pesoStatsSavesValueLabel));
        contenido.add(Box.createVerticalStrut(2));
        contenido.add(crearInfoRow("Peso total:", pesoTotalValueLabel));
        card.add(contenido, BorderLayout.CENTER);
        return card;
    }

    private JPanel crearTarjetaConexiones() {
        CardPanel card = new CardPanel(new BorderLayout(), new Insets(12, 12, 12, 12));

        JLabel titulo = crearTituloTarjeta("Últimas conexiones");
        card.add(titulo, BorderLayout.NORTH);

        conexionesPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        card.add(conexionesPanel, BorderLayout.CENTER);
        return card;
    }

    private JPanel crearPanelPreview() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(320, 320));
        panel.setMinimumSize(new Dimension(260, 260));

        JPanel overlayControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        overlayControls.setOpaque(false);
        overlayControls.add(generarPreviewButton);
        overlayControls.add(previewMenuButton);

        JPanel overlayCorner = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        overlayCorner.setOpaque(false);
        overlayCorner.add(overlayControls);

        JPanel progressWrap = new JPanel(new BorderLayout());
        progressWrap.setOpaque(false);
        progressWrap.add(previewSpinner, BorderLayout.CENTER);
        progressWrap.setVisible(false);

        JPanel checkerboardPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    int size = 16;
                    Color base = AppTheme.getPanelBackground();
                    Color light = AppTheme.tint(base, Color.WHITE, 0.10f);
                    Color dark = AppTheme.darken(base, 0.05f);

                    for (int y = 0; y < getHeight(); y += size) {
                        for (int x = 0; x < getWidth(); x += size) {
                            boolean even = ((x / size) + (y / size)) % 2 == 0;
                            g2.setColor(even ? light : dark);
                            g2.fillRect(x, y, size, size);
                        }
                    }
                } finally {
                    g2.dispose();
                }
            }
        };
        checkerboardPanel.setOpaque(true);
        checkerboardPanel.add(previewImageLabel, BorderLayout.CENTER);

        JLayeredPane layeredPreview = new JLayeredPane() {
            @Override
            public void doLayout() {
                int w = getWidth();
                int h = getHeight();
                checkerboardPanel.setBounds(0, 0, w, h);

                Dimension pref = overlayCorner.getPreferredSize();
                int x = Math.max(0, w - pref.width - 8);
                int y = Math.max(0, h - pref.height - 8);
                overlayCorner.setBounds(x, y, pref.width, pref.height);

                Dimension progressPref = progressWrap.getPreferredSize();
                int progressWidth = progressPref.width;
                int progressHeight = progressPref.height;
                int progressX = Math.max(0, (w - progressWidth) / 2);
                int progressY = Math.max(0, (h - progressHeight) / 2);
                progressWrap.setBounds(progressX, progressY, progressWidth, progressHeight);
            }
        };
        layeredPreview.setOpaque(false);
        layeredPreview.setPreferredSize(new Dimension(320, 320));
        layeredPreview.setMinimumSize(new Dimension(260, 260));
        layeredPreview.add(checkerboardPanel, JLayeredPane.DEFAULT_LAYER);
        layeredPreview.add(overlayCorner, JLayeredPane.PALETTE_LAYER);
        layeredPreview.add(progressWrap, JLayeredPane.MODAL_LAYER);

        panel.add(layeredPreview, BorderLayout.CENTER);
        panel.putClientProperty("previewProgressWrap", progressWrap);
        return panel;
    }

    private void refrescarDatos() {
        Server server = gestorServidores.getServidorSeleccionado();
        actualizandoComboMundos = true;
        mundosCombo.removeAllItems();
        mundoActivoActual = null;

        if (server == null) {
            limpiarVistaSinServidor();
            actualizandoComboMundos = false;
            return;
        }

        try {
            GestorMundos.sincronizarMundosServidor(server);
            List<World> mundos = GestorMundos.listarMundos(server);
            mundoActivoActual = GestorMundos.getMundoActivo(server);

            for (World mundo : mundos) {
                mundosCombo.addItem(mundo);
            }

            if (!mundos.isEmpty()) {
                seleccionarMundoEnCombo(mundoActivoActual);
                if (mundosCombo.getSelectedItem() == null) {
                    mundosCombo.setSelectedIndex(0);
                }
            }
            setControlesActivos(true);
        } catch (RuntimeException ex) {
            tiempoRealValueLabel.setText("-");
            seedValueLabel.setText("-");
            lastPlayedValueLabel.setText("-");
            versionValueLabel.setText("-");
            tipoMundoValueLabel.setText("-");
            pesoMundoValueLabel.setText("-");
            pesoStatsSavesValueLabel.setText("-");
            pesoTotalValueLabel.setText("-");
            mostrarTextoPreview("Error cargando mundos.");
            setControlesActivos(false);
            renderConexiones(List.of());
        } finally {
            actualizandoComboMundos = false;
        }
        actualizarVistaMundos();
    }

    private void limpiarVistaSinServidor() {
        tiempoRealValueLabel.setText("-");
        seedValueLabel.setText("-");
        lastPlayedValueLabel.setText("-");
        versionValueLabel.setText("-");
        tipoMundoValueLabel.setText("-");
        pesoMundoValueLabel.setText("-");
        pesoStatsSavesValueLabel.setText("-");
        pesoTotalValueLabel.setText("-");
        mostrarTextoPreview("Selecciona un servidor para gestionar sus mundos.");
        renderConexiones(List.of());
        setControlesActivos(false);
    }

    private void actualizarVistaMundos() {
        updateUseWorldButtonState();
        actualizarLabelsDatosServidor();
        actualizarPreviewSeleccionada();
        actualizarTextoBotonPreview();
        actualizarDatosAlmacenamiento();
        actualizarConexionesRecientes();
    }

    private void actualizarLabelsDatosServidor() {
        Server server = gestorServidores.getServidorSeleccionado();
        if (server == null) {
            limpiarVistaSinServidor();
            return;
        }

        World mundo = getMundoSeleccionadoOActivo();
        if (mundo == null) {
            tiempoRealValueLabel.setText("-");
            seedValueLabel.setText("-");
            lastPlayedValueLabel.setText("-");
            versionValueLabel.setText("-");
            tipoMundoValueLabel.setText("-");
            return;
        }

        long ticksArchivo = WorldDataReader.getActiveTicks(mundo);
        tiempoRealValueLabel.setText(ticksArchivo >= 0L ? formatearTiempo(ticksArchivo) : "-");
        String lastPlayed = WorldDataReader.getLastPlayed(mundo);
        lastPlayedValueLabel.setText(valorOPlaceholder(lastPlayed));
        versionValueLabel.setText(valorOPlaceholder(WorldDataReader.getVersionName(mundo)));
        tipoMundoValueLabel.setText(valorOPlaceholder(leerTipoMundo(mundo)));
        seedValueLabel.setText(valorOPlaceholder(WorldDataReader.getSeed(mundo)));
    }

    private void actualizarDatosAlmacenamiento() {
        World mundo = getMundoSeleccionadoOActivo();
        if (mundo == null || mundo.getWorldDir() == null || mundo.getWorldDir().isBlank()) {
            pesoMundoValueLabel.setText("-");
            pesoStatsSavesValueLabel.setText("-");
            pesoTotalValueLabel.setText("-");
            return;
        }

        Path worldDir = Path.of(mundo.getWorldDir());
        if (!Files.isDirectory(worldDir)) {
            pesoMundoValueLabel.setText("-");
            pesoStatsSavesValueLabel.setText("-");
            pesoTotalValueLabel.setText("-");
            return;
        }

        long total = calcularTamanoDirectorio(worldDir);
        long datosJugador = calcularTamanoDirectorio(worldDir.resolve("playerdata"))
                + calcularTamanoDirectorio(worldDir.resolve("stats"))
                + calcularTamanoDirectorio(worldDir.resolve("advancements"))
                + calcularTamanoDirectorio(worldDir.resolve("data"));

        long regiones = calcularTamanoDirectorio(worldDir.resolve("region"))
                + calcularTamanoDirectorio(worldDir.resolve("entities"))
                + calcularTamanoDirectorio(worldDir.resolve("poi"))
                + calcularTamanoDirectorio(worldDir.resolve("DIM-1"))
                + calcularTamanoDirectorio(worldDir.resolve("DIM1"))
                + calcularTamanoDirectorio(worldDir.resolve("dimensions"));

        if (regiones <= 0L) {
            regiones = Math.max(0L, total - datosJugador);
        }

        pesoMundoValueLabel.setText(formatearTamano(regiones));
        pesoStatsSavesValueLabel.setText(formatearTamano(datosJugador));
        pesoTotalValueLabel.setText(formatearTamano(total));
    }

    private void actualizarConexionesRecientes() {
        Server server = gestorServidores.getServidorSeleccionado();
        if (server == null) {
            renderConexiones(List.of());
            return;
        }
        renderConexiones(obtenerUltimasConexiones(server));
    }

    private List<RecentConnection> obtenerUltimasConexiones(Server server) {
        List<String> rawLogLines = server.getRawLogLines();
        if (rawLogLines == null || rawLogLines.isEmpty()) {
            return List.of();
        }

        java.util.ArrayList<RecentConnection> conexiones = new java.util.ArrayList<>();
        LocalDate fechaBase = LocalDate.now();

        for (int i = rawLogLines.size() - 1; i >= 0 && conexiones.size() < 4; i--) {
            String raw = rawLogLines.get(i);
            if (raw == null || raw.isBlank()) continue;

            Matcher joinMatcher = JOIN.matcher(raw);
            if (!joinMatcher.find()) continue;

            String jugador = joinMatcher.group(1);
            String timestamp = fechaBase.format(FORMATO_FECHA) + " - --:--:--";
            Matcher horaMatcher = HORA_LOG.matcher(raw);
            if (horaMatcher.find()) {
                try {
                    LocalTime hora = LocalTime.parse(horaMatcher.group(1), FORMATO_HORA_LOG);
                    timestamp = FORMATO_CONEXION.format(fechaBase.atTime(hora));
                } catch (DateTimeParseException ignored) {
                }
            }

            conexiones.add(new RecentConnection(jugador, timestamp));
        }

        return conexiones;
    }

    private void renderConexiones(List<RecentConnection> conexiones) {
        conexionesPanel.removeAll();

        if (conexiones == null || conexiones.isEmpty()) {
            JLabel vacio = new JLabel("No hay conexiones recientes.");
            vacio.setForeground(AppTheme.getMutedForeground());
            vacio.setAlignmentX(Component.LEFT_ALIGNMENT);
            conexionesPanel.add(vacio);
        } else {
            for (int i = 0; i < conexiones.size(); i++) {
                conexionesPanel.add(crearFilaConexion(conexiones.get(i)));
                if (i + 1 < conexiones.size()) {
                    conexionesPanel.add(Box.createVerticalStrut(8));
                }
            }
        }

        conexionesPanel.revalidate();
        conexionesPanel.repaint();
    }

    private JPanel crearFilaConexion(RecentConnection conexion) {
        JPanel fila = new JPanel(new BorderLayout(10, 0));
        fila.setOpaque(false);
        fila.setAlignmentX(Component.LEFT_ALIGNMENT);
        fila.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        JLabel avatar = new JLabel(obtenerInicial(conexion.username()), SwingConstants.CENTER);
        avatar.setPreferredSize(new Dimension(30, 30));
        avatar.setMinimumSize(new Dimension(30, 30));
        avatar.setOpaque(true);
        avatar.setBackground(AppTheme.getSoftSelectionBackground());
        avatar.setForeground(AppTheme.getForeground());
        avatar.setBorder(AppTheme.createRoundedBorder(new Insets(4, 4, 4, 4), 1f));
        avatar.setFont(avatar.getFont().deriveFont(Font.BOLD, 12f));

        JLabel nombre = new JLabel(conexion.username());
        JLabel fecha = new JLabel(conexion.timestamp());
        fecha.setForeground(AppTheme.getMutedForeground());

        fila.add(avatar, BorderLayout.WEST);
        fila.add(nombre, BorderLayout.CENTER);
        fila.add(fecha, BorderLayout.EAST);
        return fila;
    }

    private World getMundoSeleccionadoOActivo() {
        World mundo = (World) mundosCombo.getSelectedItem();
        return mundo != null ? mundo : mundoActivoActual;
    }

    private String formatearTiempo(long ticks) {
        if (ticks <= 0) return "0 s";

        long totalSegundos = ticks / 20L;
        long horas = totalSegundos / 3600L;
        long minutos = (totalSegundos % 3600L) / 60L;
        long segundos = totalSegundos % 60L;

        if (horas > 0) return horas + " h " + minutos + " min";
        if (minutos > 0) return minutos + " min " + segundos + " s";
        return segundos + " s";
    }

    private void cambiarMundoSeleccionado() {
        World mundo = (World) mundosCombo.getSelectedItem();
        if (GestorMundos.cambiarMundo(gestorServidores.getServidorSeleccionado(), mundo, this)) {
            refrescarDatos();
            notificarCambioDeMundo();
        }
    }

    private void abrirDialogoNuevoMundo() {
        Server server = gestorServidores.getServidorSeleccionado();
        if (server == null) {
            JOptionPane.showMessageDialog(this, "No hay un servidor seleccionado.", "Generar mundo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JTextField nombreField = new JTextField(MinecraftConstants.DEFAULT_WORLD_NAME + "-nuevo");
        JTextField semillaField = new JTextField();
        JComboBox<String> tipoBox = new JComboBox<>(MinecraftConstants.WORLD_TYPES.toArray(String[]::new));
        JComboBox<String> gamemodeBox = new JComboBox<>(MinecraftConstants.GAMEMODES.toArray(String[]::new));
        JComboBox<String> dificultadBox = new JComboBox<>(MinecraftConstants.DIFFICULTIES.toArray(String[]::new));
        JCheckBox estructurasBox = new JCheckBox("Generar estructuras", true);
        JCheckBox hardcoreBox = new JCheckBox("Hardcore", false);
        JCheckBox netherBox = new JCheckBox("Permitir Nether", true);
        JCheckBox activarBox = new JCheckBox("Seleccionar al crear", true);

        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 6));
        panel.add(new JLabel("Nombre del mundo"));
        panel.add(nombreField);
        panel.add(new JLabel("Semilla (opcional)"));
        panel.add(semillaField);
        panel.add(new JLabel("Tipo de mundo"));
        panel.add(tipoBox);
        panel.add(new JLabel("Gamemode por defecto"));
        panel.add(gamemodeBox);
        panel.add(new JLabel("Dificultad"));
        panel.add(dificultadBox);
        panel.add(estructurasBox);
        panel.add(hardcoreBox);
        panel.add(netherBox);
        panel.add(activarBox);

        int result = JOptionPane.showConfirmDialog(this, panel, "Generar nuevo mundo", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        GestorMundos.WorldGenerationSettings settings = new GestorMundos.WorldGenerationSettings(
                nombreField.getText(),
                semillaField.getText(),
                String.valueOf(tipoBox.getSelectedItem()),
                estructurasBox.isSelected(),
                hardcoreBox.isSelected(),
                String.valueOf(gamemodeBox.getSelectedItem()),
                String.valueOf(dificultadBox.getSelectedItem()),
                netherBox.isSelected(),
                activarBox.isSelected()
        );

        if (GestorMundos.generarNuevoMundo(server, settings, this)) {
            refrescarDatos();
            notificarCambioDeMundo();
        }
    }

    private void seleccionarMundoEnCombo(World objetivo) {
        if (objetivo == null) return;
        for (int i = 0; i < mundosCombo.getItemCount(); i++) {
            World candidato = mundosCombo.getItemAt(i);
            if (candidato != null && Objects.equals(candidato.getWorldName(), objetivo.getWorldName())) {
                mundosCombo.setSelectedIndex(i);
                return;
            }
        }
    }

    private void notificarCambioDeMundo() {
        if (onWorldChanged != null) {
            onWorldChanged.run();
        }
    }

    private void setControlesActivos(boolean activos) {
        Server server = gestorServidores.getServidorSeleccionado();
        boolean hayServidor = activos && server != null;
        boolean hayMundos = hayServidor && mundosCombo.getItemCount() > 0;

        mundosCombo.setEnabled(hayMundos);
        refrescarButton.setEnabled(hayServidor);
        importarButton.setEnabled(hayServidor);
        exportarButton.setEnabled(hayMundos);
        generarButton.setEnabled(hayServidor);
        generarPreviewButton.setEnabled(hayMundos);
        previewMenuButton.setEnabled(hayMundos);
        updateUseWorldButtonState();
    }

    private void generarPreviewMundoSeleccionado() {
        World mundo = getMundoSeleccionadoOActivo();
        if (mundo == null) {
            JOptionPane.showMessageDialog(this, "No hay un mundo seleccionado.", "Generar preview", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Path outputPath = getPreviewPath(mundo);
        boolean habiaPreviewAnterior = Files.isRegularFile(outputPath);
        previewGenerationInProgress = true;
        previewImageLabel.clearImage();
        setPreviewProgressVisible(true);
        generarPreviewButton.setEnabled(false);
        previewMenuButton.setEnabled(false);
        MCARenderer renderer = new MCARenderer();
        WorldDataReader.SpawnPoint spawnPoint = WorldDataReader.getSpawnPoint(mundo);
        SwingWorker<PreviewGenerationResult, Void> worker = new SwingWorker<>() {
            @Override
            protected PreviewGenerationResult doInBackground() throws Exception {
                List<Path> regionesPreview = encontrarRegionesPreview(mundo, usarTodoEnPreview, spawnPoint);
                if (regionesPreview.isEmpty()) {
                    return new PreviewGenerationResult(outputPath, true);
                }
                MCARenderer.WorldPoint markerPoint = mostrarSpawnEnPreview && spawnPoint != null
                        ? new MCARenderer.WorldPoint(spawnPoint.x(), spawnPoint.z())
                        : null;
                MCARenderer.RenderOptions renderOptions = MCARenderer.RenderOptions.defaults()
                        .withShadeByHeight(sombreadoEnPreview)
                        .withPreferSquareCrop(!usarTodoEnPreview);
                BufferedImage preview = renderer.renderWorld(regionesPreview, renderOptions, markerPoint);
                guardarPreview(preview, outputPath);
                return new PreviewGenerationResult(outputPath, false);
            }

            @Override
            protected void done() {
                previewGenerationInProgress = false;
                updateUseWorldButtonState();
                setPreviewProgressVisible(false);
                generarPreviewButton.setEnabled(mundosCombo.isEnabled() && getMundoSeleccionadoOActivo() != null);
                previewMenuButton.setEnabled(mundosCombo.isEnabled() && getMundoSeleccionadoOActivo() != null);
                try {
                    PreviewGenerationResult result = get();
                    if (result.sinRegiones()) {
                        restaurarPreviewAnteriorSiExiste(habiaPreviewAnterior);
                        JOptionPane.showMessageDialog(PanelMundo.this,
                                "El mundo no contiene archivos .mca en la carpeta region.",
                                "Generar preview",
                                JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    actualizarPreviewSeleccionada();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    restaurarPreviewAnteriorSiExiste(habiaPreviewAnterior);
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    String message = cause.getMessage();
                    if (message == null || message.isBlank()) {
                        message = cause.getClass().getSimpleName();
                    }
                    restaurarPreviewAnteriorSiExiste(habiaPreviewAnterior);
                    JOptionPane.showMessageDialog(PanelMundo.this,
                            "No se ha podido generar la preview: " + message,
                            "Generar preview",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private List<Path> encontrarRegionesPreview(World mundo, boolean generarTodo, WorldDataReader.SpawnPoint spawnPoint) throws Exception {
        Path regionDir = WorldDataReader.getOverworldRegionDirectory(mundo);
        if (regionDir == null || !Files.isDirectory(regionDir)) {
            return List.of();
        }

        MCARenderer renderer = new MCARenderer();
        try (Stream<Path> stream = Files.list(regionDir)) {
            List<Path> regiones = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mca"))
                    .filter(path -> {
                        try {
                            return Files.size(path) > 0L;
                        } catch (IOException ex) {
                            return false;
                        }
                    })
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();

            if (generarTodo) {
                return seleccionarGrupoPrincipalRegiones(regiones, spawnPoint);
            }

            List<Path> visibles = IntStream.range(0, regiones.size())
                    .parallel()
                    .filter(i -> {
                        try {
                            return tieneRegionVisibleCacheada(renderer, regiones.get(i));
                        } catch (IOException ex) {
                            return false;
                        }
                    })
                    .mapToObj(regiones::get)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
            List<Path> regionesBase = visibles.isEmpty() ? regiones : visibles;
            return seleccionarGrupoPrincipalRegiones(regionesBase, spawnPoint);
        }
    }

    private boolean tieneRegionVisibleCacheada(MCARenderer renderer, Path region) throws IOException {
        if (renderer == null || region == null) {
            return false;
        }

        Path normalized = region.toAbsolutePath().normalize();
        long size = Files.size(normalized);
        long lastModified = Files.getLastModifiedTime(normalized).toMillis();
        RegionVisibilityCacheEntry cached = REGION_VISIBILITY_CACHE.get(normalized);
        if (cached != null && cached.size() == size && cached.lastModified() == lastModified) {
            return cached.visible();
        }

        boolean visible = renderer.hasVisibleBlocks(normalized);
        REGION_VISIBILITY_CACHE.put(normalized, new RegionVisibilityCacheEntry(size, lastModified, visible));
        return visible;
    }

    private List<Path> seleccionarGrupoPrincipalRegiones(List<Path> regiones, WorldDataReader.SpawnPoint spawnPoint) {
        if (regiones == null || regiones.isEmpty()) {
            return List.of();
        }

        List<RegionPreviewCandidate> candidatas = new ArrayList<>();
        for (Path region : regiones) {
            RegionPreviewCandidate candidata = parsearRegionPreview(region);
            if (candidata != null) {
                candidatas.add(candidata);
            }
        }

        if (candidatas.isEmpty()) {
            return regiones;
        }

        Map<RegionKey, RegionPreviewCandidate> porClave = new HashMap<>();
        for (RegionPreviewCandidate candidata : candidatas) {
            porClave.put(candidata.key(), candidata);
        }

        List<List<RegionPreviewCandidate>> grupos = new ArrayList<>();
        Set<RegionKey> visitadas = new HashSet<>();
        for (RegionPreviewCandidate candidata : candidatas) {
            if (!visitadas.add(candidata.key())) {
                continue;
            }

            List<RegionPreviewCandidate> grupo = new ArrayList<>();
            ArrayDeque<RegionPreviewCandidate> cola = new ArrayDeque<>();
            cola.add(candidata);

            while (!cola.isEmpty()) {
                RegionPreviewCandidate actual = cola.removeFirst();
                grupo.add(actual);

                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        RegionKey vecina = new RegionKey(actual.regionX() + dx, actual.regionZ() + dz);
                        if (!visitadas.add(vecina)) {
                            continue;
                        }
                        RegionPreviewCandidate candidataVecina = porClave.get(vecina);
                        if (candidataVecina != null) {
                            cola.addLast(candidataVecina);
                        } else {
                            visitadas.remove(vecina);
                        }
                    }
                }
            }

            grupos.add(grupo);
        }

        List<RegionPreviewCandidate> mejorGrupo = elegirMejorGrupo(grupos, spawnPoint);
        return mejorGrupo.stream()
                .sorted(Comparator.comparing((RegionPreviewCandidate r) -> r.path().getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .map(RegionPreviewCandidate::path)
                .toList();
    }

    private List<RegionPreviewCandidate> elegirMejorGrupo(List<List<RegionPreviewCandidate>> grupos, WorldDataReader.SpawnPoint spawnPoint) {
        if (grupos == null || grupos.isEmpty()) {
            return List.of();
        }
        if (grupos.size() == 1) {
            return grupos.get(0);
        }

        RegionKey spawnRegion = spawnPoint == null ? null : new RegionKey(Math.floorDiv(spawnPoint.x(), 512), Math.floorDiv(spawnPoint.z(), 512));
        List<RegionPreviewCandidate> mejor = null;
        for (List<RegionPreviewCandidate> grupo : grupos) {
            if (mejor == null || compararGrupos(grupo, mejor, spawnRegion) < 0) {
                mejor = grupo;
            }
        }
        return mejor == null ? grupos.get(0) : mejor;
    }

    private int compararGrupos(List<RegionPreviewCandidate> a, List<RegionPreviewCandidate> b, RegionKey spawnRegion) {
        boolean aTieneSpawn = contieneRegion(a, spawnRegion);
        boolean bTieneSpawn = contieneRegion(b, spawnRegion);
        if (aTieneSpawn != bTieneSpawn) {
            return aTieneSpawn ? -1 : 1;
        }

        int bySize = Integer.compare(b.size(), a.size());
        if (bySize != 0) {
            return bySize;
        }

        int byWeight = Long.compare(pesoGrupo(b), pesoGrupo(a));
        if (byWeight != 0) {
            return byWeight;
        }

        if (spawnRegion != null) {
            int byDistance = Integer.compare(distanciaGrupoAlSpawn(a, spawnRegion), distanciaGrupoAlSpawn(b, spawnRegion));
            if (byDistance != 0) {
                return byDistance;
            }
        }

        RegionKey minA = minRegionKey(a);
        RegionKey minB = minRegionKey(b);
        int byZ = Integer.compare(minA.regionZ(), minB.regionZ());
        if (byZ != 0) {
            return byZ;
        }
        return Integer.compare(minA.regionX(), minB.regionX());
    }

    private boolean contieneRegion(List<RegionPreviewCandidate> grupo, RegionKey objetivo) {
        if (grupo == null || grupo.isEmpty() || objetivo == null) {
            return false;
        }
        for (RegionPreviewCandidate candidata : grupo) {
            if (candidata.key().equals(objetivo)) {
                return true;
            }
        }
        return false;
    }

    private long pesoGrupo(List<RegionPreviewCandidate> grupo) {
        long total = 0L;
        for (RegionPreviewCandidate candidata : grupo) {
            total += candidata.size();
        }
        return total;
    }

    private int distanciaGrupoAlSpawn(List<RegionPreviewCandidate> grupo, RegionKey spawnRegion) {
        int mejor = Integer.MAX_VALUE;
        for (RegionPreviewCandidate candidata : grupo) {
            int distancia = Math.abs(candidata.regionX() - spawnRegion.regionX()) + Math.abs(candidata.regionZ() - spawnRegion.regionZ());
            if (distancia < mejor) {
                mejor = distancia;
            }
        }
        return mejor;
    }

    private RegionKey minRegionKey(List<RegionPreviewCandidate> grupo) {
        RegionPreviewCandidate mejor = grupo.get(0);
        for (RegionPreviewCandidate candidata : grupo) {
            if (candidata.regionZ() < mejor.regionZ()
                    || (candidata.regionZ() == mejor.regionZ() && candidata.regionX() < mejor.regionX())) {
                mejor = candidata;
            }
        }
        return mejor.key();
    }

    private RegionPreviewCandidate parsearRegionPreview(Path region) {
        if (region == null || region.getFileName() == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)\\.mca$", Pattern.CASE_INSENSITIVE)
                .matcher(region.getFileName().toString());
        if (!matcher.matches()) {
            return null;
        }

        try {
            int regionX = Integer.parseInt(matcher.group(1));
            int regionZ = Integer.parseInt(matcher.group(2));
            long size = Files.size(region);
            return new RegionPreviewCandidate(region, regionX, regionZ, size);
        } catch (IOException | NumberFormatException ex) {
            return null;
        }
    }

    private record RegionPreviewCandidate(Path path, int regionX, int regionZ, long size) {
        private RegionKey key() {
            return new RegionKey(regionX, regionZ);
        }
    }

    private record RegionKey(int regionX, int regionZ) {}
    private record RegionVisibilityCacheEntry(long size, long lastModified, boolean visible) {}

    private void guardarPreview(BufferedImage image, Path outputPath) throws IOException {
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        ImageIO.write(image, "png", outputPath.toFile());
        actualizarTextoBotonPreview();
    }

    private void actualizarPreviewSeleccionada() {
        World mundo = getMundoSeleccionadoOActivo();
        if (mundo == null) {
            mostrarTextoPreview("La preview todavía no existe.");
            return;
        }

        Path previewPath = getPreviewPath(mundo);
        if (!Files.isRegularFile(previewPath)) {
            mostrarTextoPreview("La preview todavía no existe.");
            return;
        }

        try {
            BufferedImage image = ImageIO.read(previewPath.toFile());
            if (image == null) {
                mostrarTextoPreview("No se ha podido leer la preview.");
                return;
            }
            previewImageLabel.setImage(image);
        } catch (IOException ex) {
            mostrarTextoPreview("No se ha podido leer la preview.");
        }
    }

    private void mostrarTextoPreview(String texto) {
        previewImageLabel.setMessage(texto);
    }

    private void restaurarPreviewAnteriorSiExiste(boolean habiaPreviewAnterior) {
        if (habiaPreviewAnterior) {
            actualizarPreviewSeleccionada();
            return;
        }
        mostrarTextoPreview("No se ha podido generar la preview.");
    }

    private void setPreviewProgressVisible(boolean visible) {
        previewSpinner.setVisible(visible);
        previewSpinner.setRunning(visible);
        Container parent = previewSpinner.getParent();
        if (parent != null) {
            parent.setVisible(visible);
            parent.revalidate();
            parent.repaint();
        }
    }

    private void instalarMenuContextualPreview() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem verArchivoItem = new JMenuItem("Ver archivo");
        JMenuItem guardarComoItem = new JMenuItem("Guardar como");
        verArchivoItem.addActionListener(e -> abrirPreviewEnExplorador());
        guardarComoItem.addActionListener(e -> guardarPreviewComo());
        menu.add(verArchivoItem);
        menu.add(guardarComoItem);

        MouseAdapter popupMouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                mostrarPopupSiCorresponde(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                mostrarPopupSiCorresponde(e);
            }

            private void mostrarPopupSiCorresponde(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                boolean existePreview = existePreviewSeleccionada();
                verArchivoItem.setEnabled(existePreview);
                guardarComoItem.setEnabled(existePreview);
                menu.show(e.getComponent(), e.getX(), e.getY());
            }
        };
        previewImageLabel.addMouseListener(popupMouse);
    }

    private boolean existePreviewSeleccionada() {
        World mundo = getMundoSeleccionadoOActivo();
        return mundo != null && Files.isRegularFile(getPreviewPath(mundo));
    }

    private void abrirPreviewEnExplorador() {
        World mundo = getMundoSeleccionadoOActivo();
        if (mundo == null) {
            JOptionPane.showMessageDialog(this, "No hay un mundo seleccionado.", "Ver archivo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Path previewPath = getPreviewPath(mundo);
        if (!Files.isRegularFile(previewPath)) {
            JOptionPane.showMessageDialog(this, "La preview todavía no existe.", "Ver archivo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                new ProcessBuilder("explorer.exe", "/select,", previewPath.toAbsolutePath().toString()).start();
                return;
            }
            Desktop.getDesktop().open(previewPath.toAbsolutePath().getParent().toFile());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "No se ha podido abrir el Explorador: " + ex.getMessage(), "Ver archivo", JOptionPane.ERROR_MESSAGE);
        } catch (UnsupportedOperationException ex) {
            JOptionPane.showMessageDialog(this, "Este sistema no soporta abrir archivos desde Java.", "Ver archivo", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void guardarPreviewComo() {
        World mundo = getMundoSeleccionadoOActivo();
        if (mundo == null) {
            JOptionPane.showMessageDialog(this, "No hay un mundo seleccionado.", "Guardar como", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Path previewPath = getPreviewPath(mundo);
        if (!Files.isRegularFile(previewPath)) {
            JOptionPane.showMessageDialog(this, "La preview todavía no existe.", "Guardar como", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Guardar preview como");
        java.io.File imagenesDir = FileSystemView.getFileSystemView().getDefaultDirectory();
        if (imagenesDir != null && imagenesDir.isDirectory()) {
            chooser.setCurrentDirectory(imagenesDir);
        }
        chooser.setSelectedFile(new java.io.File(crearNombreSugeridoPreview(mundo)));

        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path destino = chooser.getSelectedFile().toPath();
        if (!destino.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
            destino = destino.resolveSibling(destino.getFileName() + ".png");
        }

        if (Files.exists(destino)) {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "El archivo ya existe. ¿Quieres reemplazarlo?",
                    "Guardar como",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        }

        try {
            Path parent = destino.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(previewPath, destino, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "No se ha podido guardar la preview: " + ex.getMessage(), "Guardar como", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String crearNombreSugeridoPreview(World mundo) {
        String nombre = mundo == null || mundo.getWorldName() == null || mundo.getWorldName().isBlank()
                ? "world"
                : mundo.getWorldName();
        String nombreSeguro = nombre.replaceAll("[\\\\/:*?\"<>|]", "_");
        String timestamp = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        return nombreSeguro + "_preview_" + timestamp + ".png";
    }

    private Path getPreviewPath(World mundo) {
        return Path.of(mundo.getWorldDir()).resolve("preview.png");
    }

    private void actualizarTextoBotonPreview() {
        World mundo = getMundoSeleccionadoOActivo();
        boolean existePreview = mundo != null && Files.isRegularFile(getPreviewPath(mundo));
        generarPreviewButton.setText(existePreview ? "Regenerar preview" : "Generar preview");
    }

    private void updateUseWorldButtonState() {
        World seleccionado = (World) mundosCombo.getSelectedItem();
        boolean hayMundoSeleccionado = mundosCombo.isEnabled() && seleccionado != null;
        boolean cambioPendiente = hayMundoSeleccionado
                && mundoActivoActual != null
                && !Objects.equals(seleccionado.getWorldName(), mundoActivoActual.getWorldName());

        applyDefaultPrimaryButtonStyle();
        usarEsteMundoButton.setEnabled(cambioPendiente);
        if (cambioPendiente) {
            usarEsteMundoButton.setBackground(AppTheme.getMainAccent());
            usarEsteMundoButton.setForeground(Color.WHITE);
            usarEsteMundoButton.setBorder(AppTheme.createAccentBorder(new Insets(6, 12, 6, 12), 1f));
        }
        usarEsteMundoButton.revalidate();
        usarEsteMundoButton.repaint();
    }

    private void applyDefaultPrimaryButtonStyle() {
        styleActionButton(usarEsteMundoButton);
    }

    private void styleActionButton(JButton button) {
        if (button == null) return;
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setOpaque(false);
        button.setContentAreaFilled(true);
        button.setBorderPainted(true);
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.setBorder(AppTheme.createRoundedBorder(new Insets(6, 12, 6, 12), 1f));
        button.setBackground(AppTheme.getBackground());
        button.setForeground(AppTheme.getForeground());
    }

    private void stylePreviewMenuButton(JButton button) {
        if (button == null) return;
        styleActionButton(button);
        button.setText("\u2630");
        button.setMargin(new Insets(4, 8, 4, 8));
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 14f));
        button.setToolTipText("Opciones de preview");
        stylePreviewOverlayButton(button);
    }

    private void stylePreviewOverlayButton(JButton button) {
        if (button == null) return;
        button.setBackground(AppTheme.getBackground());
        button.setForeground(AppTheme.getForeground());
    }

    private void instalarMenuOpcionesPreview() {
        sombreadoMenuItem.setSelected(sombreadoEnPreview);
        sombreadoMenuItem.addActionListener(e -> sombreadoEnPreview = sombreadoMenuItem.isSelected());
        mostrarSpawnMenuItem.setSelected(mostrarSpawnEnPreview);
        mostrarSpawnMenuItem.addActionListener(e -> mostrarSpawnEnPreview = mostrarSpawnMenuItem.isSelected());
        usarTodoMenuItem.setSelected(usarTodoEnPreview);
        usarTodoMenuItem.addActionListener(e -> usarTodoEnPreview = usarTodoMenuItem.isSelected());

        JPanel optionsPanel = new JPanel();
        optionsPanel.setOpaque(true);
        optionsPanel.setBackground(AppTheme.getBackground());
        optionsPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));

        stylePreviewOptionCheckBox(mostrarSpawnMenuItem);
        stylePreviewOptionCheckBox(usarTodoMenuItem);
        stylePreviewOptionCheckBox(sombreadoMenuItem);
        optionsPanel.add(createPreviewOptionsSectionLabel("Generación"));
        optionsPanel.add(sombreadoMenuItem);
        optionsPanel.add(Box.createVerticalStrut(4));
        optionsPanel.add(usarTodoMenuItem);
        optionsPanel.add(Box.createVerticalStrut(10));
        optionsPanel.add(createPreviewOptionsSectionLabel("Superposición"));
        optionsPanel.add(mostrarSpawnMenuItem);

        previewOptionsMenu.setBorder(AppTheme.createRoundedBorder(new Insets(6, 6, 6, 6), 1f));
        previewOptionsMenu.add(optionsPanel);

        previewMenuButton.addActionListener(e -> mostrarMenuOpcionesPreview(previewMenuButton));
    }

    private void mostrarMenuOpcionesPreview(Component anchor) {
        if (anchor == null) {
            return;
        }
        sombreadoMenuItem.setSelected(sombreadoEnPreview);
        mostrarSpawnMenuItem.setSelected(mostrarSpawnEnPreview);
        usarTodoMenuItem.setSelected(usarTodoEnPreview);
        previewOptionsMenu.show(anchor, 0, anchor.getHeight());
    }

    private void stylePreviewOptionCheckBox(JCheckBox checkBox) {
        if (checkBox == null) {
            return;
        }
        checkBox.setOpaque(false);
        checkBox.setFocusPainted(false);
        checkBox.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        checkBox.setForeground(AppTheme.getForeground());
    }

    private JLabel createPreviewOptionsSectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setForeground(AppTheme.getMutedForeground());
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        label.setBorder(BorderFactory.createEmptyBorder(0, 2, 4, 0));
        return label;
    }

    private void instalarInteraccionSeed() {
        Font fuenteBase = seedValueLabel.getFont();
        seedValueLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        seedValueLabel.setToolTipText("Haz click para copiar la seed.");

        seedValueLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                String texto = obtenerValorSeedActual();
                if (texto == null) return;
                java.util.Map<java.awt.font.TextAttribute, Object> attrs = new java.util.HashMap<>(fuenteBase.getAttributes());
                attrs.put(java.awt.font.TextAttribute.UNDERLINE, java.awt.font.TextAttribute.UNDERLINE_ON);
                seedValueLabel.setFont(fuenteBase.deriveFont(attrs));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                seedValueLabel.setFont(fuenteBase);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                String texto = obtenerValorSeedActual();
                if (texto == null) return;
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(texto), null);
                JOptionPane.showMessageDialog(
                        PanelMundo.this,
                        "Semilla copiada al portapapeles.",
                        "Copiado",
                        JOptionPane.INFORMATION_MESSAGE
                );
            }
        });
    }

    private String obtenerValorSeedActual() {
        String texto = seedValueLabel.getText();
        if (texto == null) return null;
        String valor = texto.trim();
        if (valor.isEmpty() || "-".equals(valor)) return null;
        return valor;
    }

    private void styleInfoLabel(JLabel label) {
        if (label == null) return;
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 14f));
    }

    private String valorOPlaceholder(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String leerTipoMundo(World mundo) {
        if (mundo == null || mundo.getWorldDir() == null || mundo.getWorldDir().isBlank()) return null;

        Path metadataPath = Path.of(mundo.getWorldDir()).resolve(WORLD_METADATA_FILE);
        if (!Files.isRegularFile(metadataPath)) return null;

        Properties metadata = new Properties();
        try (InputStream in = Files.newInputStream(metadataPath)) {
            metadata.load(in);
            return metadata.getProperty("level-type");
        } catch (IOException ex) {
            return null;
        }
    }

    private long calcularTamanoDirectorio(Path path) {
        if (path == null || !Files.exists(path)) return 0L;
        try (Stream<Path> walk = Files.walk(path)) {
            return walk.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException ex) {
                    return 0L;
                }
            }).sum();
        } catch (IOException ex) {
            return 0L;
        }
    }

    private String formatearTamano(long bytes) {
        if (bytes <= 0L) return "0 B";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double value = bytes;
        int unitIndex = 0;
        while (value >= 1024d && unitIndex < units.length - 1) {
            value /= 1024d;
            unitIndex++;
        }

        DecimalFormat format = value >= 100d || unitIndex == 0 ? new DecimalFormat("0") : new DecimalFormat("0.#");
        return format.format(value) + " " + units[unitIndex];
    }

    private String obtenerInicial(String username) {
        if (username == null || username.isBlank()) return "?";
        return username.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private static final class PreviewImagePanel extends JComponent {
        private BufferedImage image;
        private String message = "La preview todavía no existe.";
        private double zoom = 1.0d;
        private double panX = 0d;
        private double panY = 0d;
        private Point dragAnchor;
        private double dragStartPanX;
        private double dragStartPanY;

        private PreviewImagePanel() {
            setOpaque(false);
            setCursor(Cursor.getDefaultCursor());

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    updateCursor();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e) || image == null) {
                        return;
                    }
                    dragAnchor = e.getPoint();
                    dragStartPanX = panX;
                    dragStartPanY = panY;
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragAnchor == null || image == null) {
                        return;
                    }
                    panX = dragStartPanX + (e.getX() - dragAnchor.x);
                    panY = dragStartPanY + (e.getY() - dragAnchor.y);
                    clampPan();
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragAnchor = null;
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    if (image == null) {
                        return;
                    }
                    double oldZoom = zoom;
                    double factor = Math.pow(1.12d, -e.getPreciseWheelRotation());
                    zoom = Math.max(1.0d, Math.min(32.0d, zoom * factor));
                    adjustPanForZoom(oldZoom, zoom, e.getPoint());
                    clampPan();
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (image != null && e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                        resetView();
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener(mouse);
        }

        private void setImage(BufferedImage image) {
            this.image = image;
            this.message = "";
            resetView();
            updateCursor();
        }

        private void clearImage() {
            this.image = null;
            this.message = "";
            resetView();
            updateCursor();
        }

        private void setMessage(String message) {
            this.image = null;
            this.message = message == null ? "" : message;
            resetView();
            updateCursor();
        }

        private void resetView() {
            zoom = 1.0d;
            panX = 0d;
            panY = 0d;
            repaint();
        }

        private void updateCursor() {
            setCursor(image == null
                    ? Cursor.getDefaultCursor()
                    : Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        }

        private void adjustPanForZoom(double oldZoom, double newZoom, Point anchor) {
            RenderState oldState = computeState(oldZoom, panX, panY);
            if (oldState == null || anchor == null) {
                return;
            }

            double imageX = (anchor.x - oldState.drawX()) / oldState.scale();
            double imageY = (anchor.y - oldState.drawY()) / oldState.scale();
            RenderState newState = computeState(newZoom, panX, panY);
            if (newState == null) {
                return;
            }

            panX = anchor.x - (newState.baseX() + imageX * newState.scale());
            panY = anchor.y - (newState.baseY() + imageY * newState.scale());
        }

        private void clampPan() {
            RenderState state = computeState(zoom, panX, panY);
            if (state == null) {
                panX = 0d;
                panY = 0d;
                return;
            }
            panX = Math.max(-state.maxPanX(), Math.min(state.maxPanX(), panX));
            panY = Math.max(-state.maxPanY(), Math.min(state.maxPanY(), panY));
        }

        private RenderState computeState(double zoomValue, double panXValue, double panYValue) {
            if (image == null || getWidth() <= 0 || getHeight() <= 0 || image.getWidth() <= 0 || image.getHeight() <= 0) {
                return null;
            }

            double baseScale = Math.min((double) getWidth() / image.getWidth(), (double) getHeight() / image.getHeight());
            if (baseScale <= 0d) {
                return null;
            }

            double scale = baseScale * Math.max(1.0d, zoomValue);
            int drawWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int drawHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
            double baseX = (getWidth() - drawWidth) / 2.0d;
            double baseY = (getHeight() - drawHeight) / 2.0d;
            double maxPanX = Math.max(0d, (drawWidth - getWidth()) / 2.0d);
            double maxPanY = Math.max(0d, (drawHeight - getHeight()) / 2.0d);
            int drawX = (int) Math.round(baseX + Math.max(-maxPanX, Math.min(maxPanX, panXValue)));
            int drawY = (int) Math.round(baseY + Math.max(-maxPanY, Math.min(maxPanY, panYValue)));
            return new RenderState(scale, drawX, drawY, drawWidth, drawHeight, baseX, baseY, maxPanX, maxPanY);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                if (image == null) {
                    g2.setColor(AppTheme.getMutedForeground());
                    FontMetrics metrics = g2.getFontMetrics();
                    int x = Math.max(8, (getWidth() - metrics.stringWidth(message)) / 2);
                    int y = (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2;
                    g2.drawString(message, x, y);
                    return;
                }

                clampPan();
                RenderState state = computeState(zoom, panX, panY);
                if (state == null) {
                    return;
                }

                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                g2.drawImage(image, state.drawX(), state.drawY(), state.drawWidth(), state.drawHeight(), null);
            } finally {
                g2.dispose();
            }
        }

        private record RenderState(double scale, int drawX, int drawY, int drawWidth, int drawHeight,
                                   double baseX, double baseY, double maxPanX, double maxPanY) {}
    }

    private static final class PreviewSpinner extends JComponent {
        private final Timer timer;
        private int frame = 0;

        private PreviewSpinner() {
            setOpaque(false);
            setPreferredSize(new Dimension(64, 64));
            setMinimumSize(new Dimension(64, 64));
            timer = new Timer(90, e -> {
                frame = (frame + 1) % 12;
                repaint();
            });
        }

        private void setRunning(boolean running) {
            if (running) {
                if (!timer.isRunning()) {
                    timer.start();
                }
            } else {
                timer.stop();
                frame = 0;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                int radius = Math.max(14, Math.min(getWidth(), getHeight()) / 2 - 10);

                for (int i = 0; i < 12; i++) {
                    double angle = Math.toRadians((i * 30) - 90);
                    int dotSize = (i == frame) ? 12 : 8;
                    int x = (int) Math.round(cx + Math.cos(angle) * radius) - (dotSize / 2);
                    int y = (int) Math.round(cy + Math.sin(angle) * radius) - (dotSize / 2);
                    int alpha = (i == frame) ? 235 : 90;
                    g2.setColor(AppTheme.withAlpha(AppTheme.getForeground(), alpha));
                    g2.fillOval(x, y, dotSize, dotSize);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    private record RecentConnection(String username, String timestamp) {}

    private record PreviewGenerationResult(Path outputPath, boolean sinRegiones) {}
}
