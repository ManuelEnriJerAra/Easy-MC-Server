package vista;

import controlador.GestorMundos;
import controlador.GestorServidores;
import controlador.MCARenderer;
import controlador.MojangAPI;
import controlador.WorldDataReader;
import modelo.MinecraftConstants;
import modelo.Server;
import modelo.World;
import net.querz.nbt.io.NBTUtil;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.Tag;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class PanelMundo extends JPanel {
    private static final int CONNECTION_HEAD_SIZE = 24;
    private static final int PREVIEW_PLAYER_HEAD_SIZE = 24;
    private static final Pattern JOIN = Pattern.compile("([^\\s]+) joined the game");
    private static final Pattern HORA_LOG = Pattern.compile("^\\[(\\d{2}:\\d{2}:\\d{2})]\\s*");
    private static final Pattern REGION_FILE_PATTERN = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)\\.mca$", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter FORMATO_HORA_LOG = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATO_CONEXION = DateTimeFormatter.ofPattern("dd/MM/yyyy - HH:mm:ss");
    private static final String WORLD_METADATA_FILE = ".emw-world.properties";
    private static final String PREVIEW_METADATA_FILE = ".preview-overlay.properties";
    private static final Map<Path, RegionVisibilityCacheEntry> REGION_VISIBILITY_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ImageIcon> PLAYER_HEAD_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> PLAYER_HEADS_LOADING = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper = new ObjectMapper();

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
    private final JLabel previewRenderStatusLabel = new JLabel();
    private final JComboBox<World> mundosCombo = new JComboBox<>();
    private final JButton refrescarButton = new JButton("Refrescar");
    private final JButton usarEsteMundoButton = new JButton("Usar este mundo");
    private final JButton importarButton = new JButton("Importar mundo");
    private final JButton exportarButton = new JButton("Exportar mundo");
    private final JButton generarButton = new JButton("Generar nuevo");
    private final JButton generarPreviewButton = new JButton("Generar preview");
    private final JButton previewMenuButton = new JButton("\u2630");
    private JDialog previewOptionsDialog;
    private JPanel previewOptionsPanel;
    private final JCheckBox sombreadoMenuItem = new JCheckBox("Sombreado", true);
    private final JCheckBox sombreadoAguaMenuItem = new JCheckBox("Sombreado agua", true);
    private final JCheckBox colorearBiomasMenuItem = new JCheckBox("Colorear biomas", true);
    private final JCheckBox mostrarSpawnMenuItem = new JCheckBox("Mostrar spawn", false);
    private final JCheckBox mostrarJugadoresMenuItem = new JCheckBox("Mostrar jugadores", false);
    private final JCheckBox limitesChunksMenuItem = new JCheckBox("Límites de chunks", false);
    private final JCheckBox usarTodoMenuItem = new JCheckBox("Mapa completo", false);
    private final JComboBox<PreviewRenderLimitOption> limiteRenderCombo = new JComboBox<>(new PreviewRenderLimitOption[]{
            new PreviewRenderLimitOption("64x64", 64),
            new PreviewRenderLimitOption("128x128", 128),
            new PreviewRenderLimitOption("256x256", 256),
            new PreviewRenderLimitOption("512x512", 512),
            new PreviewRenderLimitOption("1024x1024", 1024),
            new PreviewRenderLimitOption("2048x2048", 2048),
            new PreviewRenderLimitOption("4096x4096", 4096),
            new PreviewRenderLimitOption("8192x8192", 8192),
            new PreviewRenderLimitOption("16384x16384", 16384),
            new PreviewRenderLimitOption("Ilimitado", 0)
    });
    private final JComboBox<PreviewCenterOption> centroRenderCombo = new JComboBox<>();
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
    private boolean sombreadoAguaEnPreview = true;
    private boolean colorearBiomasEnPreview = true;
    private boolean mostrarSpawnEnPreview = false;
    private boolean mostrarJugadoresEnPreview = false;
    private boolean limitesChunksEnPreview = false;
    private boolean usarTodoEnPreview = false;
    private int limiteMaximoRenderPreview = 512;
    private String centroRenderPreviewId = "spawn";
    private boolean previewGenerationInProgress = false;
    private SwingWorker<PreviewGenerationResult, Void> previewGenerationWorker;
    private final AtomicLong previewGenerationSequence = new AtomicLong();
    private boolean previewDisponibleAntesDeGenerar = false;
    private String previewGenerationServerId;
    private String previewGenerationServerName;
    private String previewGenerationWorldDir;
    private String previewGenerationWorldName;

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
        stylePreviewStatusLabel(previewRenderStatusLabel);
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
        overlayCorner.setLayout(new BoxLayout(overlayCorner, BoxLayout.Y_AXIS));
        previewRenderStatusLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        overlayControls.setAlignmentX(Component.RIGHT_ALIGNMENT);
        overlayCorner.add(previewRenderStatusLabel);
        overlayCorner.add(Box.createVerticalStrut(6));
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
        actualizarTextoBotonPreview();
        actualizarIndicadorRenderEnCurso();
    }

    private void actualizarVistaMundos() {
        updateUseWorldButtonState();
        actualizarLabelsDatosServidor();
        actualizarPreviewSeleccionada();
        actualizarTextoBotonPreview();
        actualizarIndicadorRenderEnCurso();
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
        renderConexiones(obtenerUltimasConexiones(server, getMundoSeleccionadoOActivo()));
    }

    private List<RecentConnection> obtenerUltimasConexiones(Server server, World mundo) {
        List<String> rawLogLines = server.getRawLogLines();
        java.util.ArrayList<RecentConnection> conexiones = new java.util.ArrayList<>();
        if (rawLogLines != null && !rawLogLines.isEmpty()) {
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

                conexiones.add(new RecentConnection(jugador, timestamp, null));
            }
        }

        if (!conexiones.isEmpty()) {
            return conexiones;
        }

        return obtenerUltimosJugadoresDesdePlayerdata(server, mundo);
    }

    private List<RecentConnection> obtenerUltimosJugadoresDesdePlayerdata(Server server, World mundo) {
        return obtenerJugadoresRecientesDesdePlayerdata(server, mundo).stream()
                .map(player -> new RecentConnection(
                        player.username(),
                        FORMATO_CONEXION.format(player.lastSeen().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()),
                        "X: " + player.point().x() + " Z: " + player.point().z(),
                        player.lastSeen().toMillis()
                ))
                .toList();
    }

    private List<PreviewPlayerData> obtenerJugadoresRecientesDesdePlayerdata(Server server, World mundo) {
        if (server == null || mundo == null || mundo.getWorldDir() == null || mundo.getWorldDir().isBlank()) {
            return List.of();
        }

        Path playerdataDir = Path.of(mundo.getWorldDir()).resolve("playerdata");
        if (!Files.isDirectory(playerdataDir)) {
            return List.of();
        }

        Map<UUID, String> nombresPorUuid = cargarNombresJugadores(server);
        try (Stream<Path> stream = Files.list(playerdataDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName() != null)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".dat"))
                    .map(path -> leerJugadorRecienteDesdePlayerdata(path, nombresPorUuid))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(PreviewPlayerData::lastSeen, Comparator.reverseOrder()))
                    .limit(4)
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    private PreviewPlayerData leerJugadorRecienteDesdePlayerdata(Path playerFile, Map<UUID, String> nombresPorUuid) {
        try {
            String fileName = playerFile.getFileName().toString();
            String uuidText = fileName.substring(0, fileName.length() - 4);
            UUID uuid = UUID.fromString(uuidText);
            String username = nombresPorUuid.getOrDefault(uuid, uuidText);
            FileTime lastModified = Files.getLastModifiedTime(playerFile);
            MCARenderer.WorldPoint point = leerPosicionJugador(playerFile);
            if (point == null) {
                return null;
            }
            return new PreviewPlayerData(username, point, lastModified);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<PreviewPlayerPoint> obtenerJugadoresRecientesParaOverlay(World mundo) {
        return obtenerJugadoresRecientesDesdePlayerdata(gestorServidores.getServidorSeleccionado(), mundo).stream()
                .map(player -> new PreviewPlayerPoint(player.username(), player.point()))
                .toList();
    }

    private MCARenderer.WorldPoint leerPosicionJugador(Path playerFile) {
        try {
            NamedTag namedTag = NBTUtil.read(playerFile.toFile());
            if (namedTag == null || !(namedTag.getTag() instanceof CompoundTag root)) {
                return null;
            }

            ListTag<?> pos = root.getListTag("Pos");
            if (pos == null || pos.size() < 3) {
                return null;
            }

            Double x = leerNumeroTag(pos.get(0));
            Double z = leerNumeroTag(pos.get(2));
            if (x == null || z == null) {
                return null;
            }

            return new MCARenderer.WorldPoint((int) Math.round(x), (int) Math.round(z));
        } catch (Exception ex) {
            return null;
        }
    }

    private Double leerNumeroTag(Tag<?> tag) {
        if (tag instanceof net.querz.nbt.tag.NumberTag<?> numberTag) {
            return numberTag.asDouble();
        }
        return null;
    }

    private Map<UUID, String> cargarNombresJugadores(Server server) {
        if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return Map.of();
        }

        Path usercachePath = Path.of(server.getServerDir()).resolve("usercache.json");
        if (!Files.isRegularFile(usercachePath)) {
            return Map.of();
        }

        try {
            JsonNode root = objectMapper.readTree(usercachePath.toFile());
            if (root == null || !root.isArray()) {
                return Map.of();
            }

            Map<UUID, String> nombres = new HashMap<>();
            for (JsonNode node : root) {
                if (node == null) continue;
                String uuidText = node.path("uuid").asString(null);
                String name = node.path("name").asString(null);
                if (uuidText == null || name == null || name.isBlank()) continue;
                try {
                    nombres.put(UUID.fromString(uuidText), name.strip());
                } catch (IllegalArgumentException ignored) {
                }
            }
            return nombres;
        } catch (Exception ex) {
            return Map.of();
        }
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
        fila.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));

        JLabel avatar = new JLabel(obtenerInicial(conexion.username()), SwingConstants.CENTER);
        avatar.setPreferredSize(new Dimension(CONNECTION_HEAD_SIZE, CONNECTION_HEAD_SIZE));
        avatar.setMinimumSize(new Dimension(CONNECTION_HEAD_SIZE, CONNECTION_HEAD_SIZE));
        avatar.setOpaque(false);
        avatar.setBackground(new Color(0, 0, 0, 0));
        avatar.setForeground(AppTheme.getForeground());
        avatar.setBorder(BorderFactory.createEmptyBorder());
        avatar.setFont(avatar.getFont().deriveFont(Font.BOLD, 12f));
        aplicarCabezaJugadorConexion(avatar, conexion.username());

        JLabel nombre = new JLabel(conexion.username());
        JLabel fecha = new JLabel(conexion.timestamp());
        fecha.setForeground(AppTheme.getMutedForeground());
        JLabel ubicacion = new JLabel(valorOPlaceholder(conexion.location()));
        ubicacion.setForeground(AppTheme.getMutedForeground());
        ubicacion.setFont(ubicacion.getFont().deriveFont(Font.PLAIN, 11f));

        JPanel texto = new JPanel();
        texto.setOpaque(false);
        texto.setLayout(new BoxLayout(texto, BoxLayout.Y_AXIS));
        texto.add(nombre);
        if (conexion.location() != null && !conexion.location().isBlank()) {
            texto.add(ubicacion);
        }

        fila.add(avatar, BorderLayout.WEST);
        fila.add(texto, BorderLayout.CENTER);
        fila.add(fecha, BorderLayout.EAST);
        return fila;
    }

    private void aplicarCabezaJugadorConexion(JLabel avatar, String username) {
        if (avatar == null || username == null || username.isBlank()) {
            return;
        }

        String key = username.strip().toLowerCase(Locale.ROOT);
        ImageIcon cachedHead = PLAYER_HEAD_CACHE.get(key);
        if (cachedHead != null && cachedHead.getImage() != null) {
            avatar.setText(null);
            avatar.setIcon(cachedHead);
            return;
        }

        avatar.setText(obtenerInicial(username));
        avatar.setIcon(null);
        cargarCabezaJugadorPreviewAsync(username, () -> {
            ImageIcon loadedHead = PLAYER_HEAD_CACHE.get(key);
            if (loadedHead != null && loadedHead.getImage() != null) {
                avatar.setText(null);
                avatar.setIcon(loadedHead);
            }
            avatar.revalidate();
            avatar.repaint();
        });
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

        if (previewGenerationInProgress) {
            if (mismoContextoRenderEnCurso(gestorServidores.getServidorSeleccionado(), mundo)) {
                cancelarGeneracionPreview();
                return;
            }
            JOptionPane.showMessageDialog(
                    this,
                    construirMensajeRenderEnCurso(),
                    "Generar preview",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        long generationId = previewGenerationSequence.incrementAndGet();
        String worldDir = mundo.getWorldDir();
        Server selectedServer = gestorServidores.getServidorSeleccionado();
        String serverId = selectedServer == null ? null : selectedServer.getId();
        Path outputPath = getPreviewPath(mundo);
        boolean habiaPreviewAnterior = Files.isRegularFile(outputPath);
        previewDisponibleAntesDeGenerar = habiaPreviewAnterior;
        previewGenerationInProgress = true;
        previewGenerationServerId = serverId;
        previewGenerationServerName = selectedServer == null
                ? null
                : (selectedServer.getDisplayName() == null || selectedServer.getDisplayName().isBlank()
                ? selectedServer.getId()
                : selectedServer.getDisplayName());
        previewGenerationWorldDir = worldDir;
        previewGenerationWorldName = mundo.getWorldName();
        previewImageLabel.clearImage();
        setPreviewProgressVisible(true);
        actualizarTextoBotonPreview();
        actualizarIndicadorRenderEnCurso();
        long previewStartNanos = System.nanoTime();
        MCARenderer renderer = new MCARenderer();
        WorldDataReader.SpawnPoint spawnPoint = WorldDataReader.getSpawnPoint(mundo);
        SwingWorker<PreviewGenerationResult, Void> worker = new SwingWorker<>() {
            @Override
            protected PreviewGenerationResult doInBackground() throws Exception {
                List<PreviewPlayerData> recentPlayers = obtenerJugadoresRecientesDesdePlayerdata(selectedServer, mundo);
                MCARenderer.WorldPoint centerPoint = usarTodoEnPreview ? null : resolverCentroRender(mundo, spawnPoint, recentPlayers);
                List<Path> regionesPreview = encontrarRegionesPreview(mundo, usarTodoEnPreview, spawnPoint, centerPoint);
                if (isCancelled()) {
                    return null;
                }
                if (regionesPreview.isEmpty()) {
                    return new PreviewGenerationResult(outputPath, true, null, null);
                }
                MCARenderer.RenderOptions renderOptions = MCARenderer.RenderOptions.defaults()
                        .withShadeByHeight(sombreadoEnPreview)
                        .withWaterSubsurfaceShading(sombreadoAguaEnPreview)
                        .withBiomeColoring(colorearBiomasEnPreview)
                        .withPreferSquareCrop(!usarTodoEnPreview);
                if (!usarTodoEnPreview && centerPoint != null && limiteMaximoRenderPreview > 0) {
                    RenderWorldBounds bounds = resolverLimitesRender(regionesPreview, centerPoint, limiteMaximoRenderPreview);
                    if (bounds != null) {
                        renderOptions = renderOptions.withWorldBounds(
                                bounds.minBlockX(),
                                bounds.maxBlockX(),
                                bounds.minBlockZ(),
                                bounds.maxBlockZ()
                        );
                    }
                }
                MCARenderer.RenderedWorld renderedWorld = renderer.renderWorldWithMetadata(regionesPreview, renderOptions);
                if (isCancelled()) {
                    return null;
                }
                CroppedPreview croppedPreview = usarTodoEnPreview
                        ? new CroppedPreview(renderedWorld.image(), renderedWorld.originBlockX(), renderedWorld.originBlockZ())
                        : recortarPreviewPorLimite(renderedWorld, centerPoint, limiteMaximoRenderPreview);
                BufferedImage preview = croppedPreview.image();
                guardarPreview(preview, outputPath);
                PreviewOverlayData overlayData = null;
                try {
                    overlayData = new PreviewOverlayData(
                            croppedPreview.originBlockX(),
                            croppedPreview.originBlockZ(),
                            renderedWorld.pixelsPerBlock(),
                            spawnPoint == null ? null : new MCARenderer.WorldPoint(spawnPoint.x(), spawnPoint.z()),
                            recentPlayers.stream()
                                    .map(player -> new PreviewPlayerPoint(player.username(), player.point()))
                                    .toList()
                    );
                    guardarPreviewOverlayData(mundo, overlayData);
                } catch (RuntimeException ex) {
                    System.out.println("[PanelMundo] No se ha podido construir la metadata de overlay de la preview: " + ex.getMessage());
                    ex.printStackTrace(System.out);
                }
                return new PreviewGenerationResult(outputPath, false, preview, overlayData);
            }

            @Override
            protected void done() {
                boolean isCurrentWorker = previewGenerationWorker == this && previewGenerationSequence.get() == generationId;
                if (isCurrentWorker) {
                    previewGenerationInProgress = false;
                    previewGenerationWorker = null;
                    limpiarContextoRenderEnCurso();
                    updateUseWorldButtonState();
                    setPreviewProgressVisible(false);
                    actualizarTextoBotonPreview();
                    actualizarIndicadorRenderEnCurso();
                }
                try {
                    if (isCancelled()) {
                        restaurarPreviewAnteriorSiExiste(habiaPreviewAnterior);
                        return;
                    }
                    PreviewGenerationResult result = get();
                    if (result == null) {
                        return;
                    }
                    if (result.sinRegiones()) {
                        restaurarPreviewAnteriorSiExiste(habiaPreviewAnterior);
                        JOptionPane.showMessageDialog(PanelMundo.this,
                                "El mundo no contiene archivos .mca en la carpeta region.",
                                "Generar preview",
                                JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    double elapsedSeconds = (System.nanoTime() - previewStartNanos) / 1_000_000_000.0d;
                    System.out.println(String.format(Locale.ROOT, "[PanelMundo] Preview generated in %.2f seconds", elapsedSeconds));
                    boolean sigueEnMismoMundo = mismoContextoSeleccionado(serverId, worldDir);
                    if (result.preview() != null && sigueEnMismoMundo) {
                        previewImageLabel.setImage(result.preview());
                        previewImageLabel.setOverlayData(result.overlayData());
                        previewImageLabel.setChunkGridVisible(limitesChunksEnPreview);
                        previewImageLabel.setSpawnVisible(mostrarSpawnEnPreview);
                        previewImageLabel.setPlayersVisible(mostrarJugadoresEnPreview);
                    } else if (sigueEnMismoMundo) {
                        actualizarPreviewSeleccionada();
                    }
                } catch (java.util.concurrent.CancellationException ex) {
                    restaurarPreviewAnteriorSiExiste(habiaPreviewAnterior);
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
        previewGenerationWorker = worker;
        worker.execute();
    }

    private List<Path> encontrarRegionesPreview(World mundo, boolean generarTodo, WorldDataReader.SpawnPoint spawnPoint, MCARenderer.WorldPoint centerPoint) throws Exception {
        Path regionDir = WorldDataReader.getOverworldRegionDirectory(mundo);
        if (regionDir == null || !Files.isDirectory(regionDir)) {
            return List.of();
        }

        MCARenderer renderer = new MCARenderer();
        try (Stream<Path> stream = Files.list(regionDir)) {
            List<Path> regiones = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mca"))
                    .filter(this::esArchivoRegionValido)
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
                return seleccionarGrupoPrincipalRegiones(regiones, spawnPoint, centerPoint, limiteMaximoRenderPreview);
            }

            List<Path> regionesBase;
            if (centerPoint != null && limiteMaximoRenderPreview > 0) {
                regionesBase = regiones;
            } else {
                List<Path> visibles = IntStream.range(0, regiones.size())
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
                regionesBase = visibles.isEmpty() ? regiones : visibles;
            }
            return seleccionarGrupoPrincipalRegiones(regionesBase, spawnPoint, centerPoint, limiteMaximoRenderPreview);
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

    private void cancelarGeneracionPreview() {
        SwingWorker<PreviewGenerationResult, Void> worker = previewGenerationWorker;
        if (worker != null) {
            worker.cancel(true);
        }
        previewGenerationInProgress = false;
        previewGenerationWorker = null;
        limpiarContextoRenderEnCurso();
        setPreviewProgressVisible(false);
        restaurarPreviewAnteriorSiExiste(previewDisponibleAntesDeGenerar);
        actualizarTextoBotonPreview();
        actualizarIndicadorRenderEnCurso();
    }

    private boolean mismoContextoSeleccionado(String expectedServerId, String expectedWorldDir) {
        Server currentServer = gestorServidores.getServidorSeleccionado();
        World currentWorld = getMundoSeleccionadoOActivo();
        String currentServerId = currentServer == null ? null : currentServer.getId();
        String currentWorldDir = currentWorld == null ? null : currentWorld.getWorldDir();
        return Objects.equals(expectedServerId, currentServerId) && Objects.equals(expectedWorldDir, currentWorldDir);
    }

    private boolean mismoContextoRenderEnCurso(Server server, World world) {
        String serverId = server == null ? null : server.getId();
        String worldDir = world == null ? null : world.getWorldDir();
        return Objects.equals(previewGenerationServerId, serverId) && Objects.equals(previewGenerationWorldDir, worldDir);
    }

    private void limpiarContextoRenderEnCurso() {
        previewGenerationServerId = null;
        previewGenerationServerName = null;
        previewGenerationWorldDir = null;
        previewGenerationWorldName = null;
    }

    private CroppedPreview recortarPreviewPorLimite(MCARenderer.RenderedWorld renderedWorld, MCARenderer.WorldPoint centerPoint, int maxBlocksPerSide) {
        if (renderedWorld == null || renderedWorld.image() == null || maxBlocksPerSide <= 0) {
            return new CroppedPreview(
                    renderedWorld == null ? null : renderedWorld.image(),
                    renderedWorld == null ? 0 : renderedWorld.originBlockX(),
                    renderedWorld == null ? 0 : renderedWorld.originBlockZ()
            );
        }

        BufferedImage image = renderedWorld.image();
        int pixelsPerBlock = Math.max(1, renderedWorld.pixelsPerBlock());
        int maxPixelSide = Math.max(1, maxBlocksPerSide * pixelsPerBlock);
        if (image.getWidth() <= maxPixelSide && image.getHeight() <= maxPixelSide) {
            return new CroppedPreview(image, renderedWorld.originBlockX(), renderedWorld.originBlockZ());
        }

        int cropWidth = Math.min(image.getWidth(), maxPixelSide);
        int cropHeight = Math.min(image.getHeight(), maxPixelSide);
        int desiredMinBlockX;
        int desiredMinBlockZ;
        if (centerPoint == null) {
            desiredMinBlockX = renderedWorld.originBlockX() + Math.max(0, ((image.getWidth() / pixelsPerBlock) - maxBlocksPerSide) / 2);
            desiredMinBlockZ = renderedWorld.originBlockZ() + Math.max(0, ((image.getHeight() / pixelsPerBlock) - maxBlocksPerSide) / 2);
        } else {
            int halfSpan = Math.max(1, maxBlocksPerSide / 2);
            desiredMinBlockX = centerPoint.x() - halfSpan;
            desiredMinBlockZ = centerPoint.z() - halfSpan;
        }

        int cropX = Math.max(0, Math.min(image.getWidth() - cropWidth, (desiredMinBlockX - renderedWorld.originBlockX()) * pixelsPerBlock));
        int cropY = Math.max(0, Math.min(image.getHeight() - cropHeight, (desiredMinBlockZ - renderedWorld.originBlockZ()) * pixelsPerBlock));

        BufferedImage cropped = image.getSubimage(cropX, cropY, cropWidth, cropHeight);
        int originBlockX = renderedWorld.originBlockX() + Math.floorDiv(cropX, pixelsPerBlock);
        int originBlockZ = renderedWorld.originBlockZ() + Math.floorDiv(cropY, pixelsPerBlock);
        return new CroppedPreview(cropped, originBlockX, originBlockZ);
    }

    private List<Path> seleccionarGrupoPrincipalRegiones(List<Path> regiones, WorldDataReader.SpawnPoint spawnPoint, MCARenderer.WorldPoint centerPoint, int limiteMaximoRender) {
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

        if (usarTodoEnPreview) {
            return candidatas.stream()
                    .sorted(Comparator.comparing((RegionPreviewCandidate r) -> r.path().getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .map(RegionPreviewCandidate::path)
                    .toList();
        }

        List<Path> regionesVentana = seleccionarRegionesParaVentana(candidatas, centerPoint, limiteMaximoRender);
        if (!regionesVentana.isEmpty()) {
            return regionesVentana;
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

        List<RegionPreviewCandidate> mejorGrupo = elegirMejorGrupo(grupos, spawnPoint, centerPoint);
        List<RegionPreviewCandidate> regionesLimitadas = limitarGrupoParaRender(mejorGrupo, centerPoint, limiteMaximoRender);
        return regionesLimitadas.stream()
                .sorted(Comparator.comparing((RegionPreviewCandidate r) -> r.path().getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .map(RegionPreviewCandidate::path)
                .toList();
    }

    private List<Path> seleccionarRegionesParaVentana(List<RegionPreviewCandidate> candidatas, MCARenderer.WorldPoint centerPoint, int limiteMaximoRender) {
        if (candidatas == null || candidatas.isEmpty() || centerPoint == null || limiteMaximoRender <= 0) {
            return List.of();
        }

        int halfSpan = Math.max(1, limiteMaximoRender / 2);
        int minBlockX = centerPoint.x() - halfSpan;
        int maxBlockX = minBlockX + limiteMaximoRender - 1;
        int minBlockZ = centerPoint.z() - halfSpan;
        int maxBlockZ = minBlockZ + limiteMaximoRender - 1;

        return candidatas.stream()
                .filter(candidate -> regionIntersectsWindow(candidate, minBlockX, maxBlockX, minBlockZ, maxBlockZ))
                .sorted(Comparator.comparing((RegionPreviewCandidate r) -> r.path().getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .map(RegionPreviewCandidate::path)
                .toList();
    }

    private RenderWorldBounds resolverLimitesRender(List<Path> regionesPreview, MCARenderer.WorldPoint centerPoint, int limiteMaximoRender) {
        if (regionesPreview == null || regionesPreview.isEmpty() || centerPoint == null || limiteMaximoRender <= 0) {
            return null;
        }

        int minRegionX = Integer.MAX_VALUE;
        int maxRegionX = Integer.MIN_VALUE;
        int minRegionZ = Integer.MAX_VALUE;
        int maxRegionZ = Integer.MIN_VALUE;
        for (Path region : regionesPreview) {
            RegionPreviewCandidate candidate = parsearRegionPreview(region);
            if (candidate == null) {
                continue;
            }
            minRegionX = Math.min(minRegionX, candidate.regionX());
            maxRegionX = Math.max(maxRegionX, candidate.regionX());
            minRegionZ = Math.min(minRegionZ, candidate.regionZ());
            maxRegionZ = Math.max(maxRegionZ, candidate.regionZ());
        }

        if (minRegionX == Integer.MAX_VALUE) {
            return null;
        }

        int availableMinBlockX = minRegionX * MCARenderer.REGION_BLOCK_SIDE;
        int availableMaxBlockX = ((maxRegionX + 1) * MCARenderer.REGION_BLOCK_SIDE) - 1;
        int availableMinBlockZ = minRegionZ * MCARenderer.REGION_BLOCK_SIDE;
        int availableMaxBlockZ = ((maxRegionZ + 1) * MCARenderer.REGION_BLOCK_SIDE) - 1;

        int span = Math.max(1, limiteMaximoRender);
        int requestedMinBlockX = centerPoint.x() - (span / 2);
        int requestedMaxBlockX = requestedMinBlockX + span - 1;
        int requestedMinBlockZ = centerPoint.z() - (span / 2);
        int requestedMaxBlockZ = requestedMinBlockZ + span - 1;

        int minBlockX = Math.max(availableMinBlockX, requestedMinBlockX);
        int maxBlockX = Math.min(availableMaxBlockX, requestedMaxBlockX);
        int minBlockZ = Math.max(availableMinBlockZ, requestedMinBlockZ);
        int maxBlockZ = Math.min(availableMaxBlockZ, requestedMaxBlockZ);
        if (minBlockX > maxBlockX || minBlockZ > maxBlockZ) {
            return null;
        }

        return new RenderWorldBounds(minBlockX, maxBlockX, minBlockZ, maxBlockZ);
    }

    private boolean regionIntersectsWindow(RegionPreviewCandidate candidate, int minBlockX, int maxBlockX, int minBlockZ, int maxBlockZ) {
        if (candidate == null) {
            return false;
        }
        int regionMinX = candidate.regionX() * MCARenderer.REGION_BLOCK_SIDE;
        int regionMaxX = regionMinX + MCARenderer.REGION_BLOCK_SIDE - 1;
        int regionMinZ = candidate.regionZ() * MCARenderer.REGION_BLOCK_SIDE;
        int regionMaxZ = regionMinZ + MCARenderer.REGION_BLOCK_SIDE - 1;
        return regionMaxX >= minBlockX
                && regionMinX <= maxBlockX
                && regionMaxZ >= minBlockZ
                && regionMinZ <= maxBlockZ;
    }

    private List<RegionPreviewCandidate> limitarGrupoParaRender(List<RegionPreviewCandidate> grupo, MCARenderer.WorldPoint centerPoint, int limiteMaximoRender) {
        if (grupo == null || grupo.isEmpty() || limiteMaximoRender <= 0) {
            return grupo == null ? List.of() : grupo;
        }

        int maxRegionsPerSide = Math.max(1, limiteMaximoRender / MCARenderer.REGION_BLOCK_SIDE);
        if (maxRegionsPerSide == Integer.MAX_VALUE) {
            return grupo;
        }

        int minRegionX = grupo.stream().mapToInt(RegionPreviewCandidate::regionX).min().orElse(0);
        int maxRegionX = grupo.stream().mapToInt(RegionPreviewCandidate::regionX).max().orElse(0);
        int minRegionZ = grupo.stream().mapToInt(RegionPreviewCandidate::regionZ).min().orElse(0);
        int maxRegionZ = grupo.stream().mapToInt(RegionPreviewCandidate::regionZ).max().orElse(0);
        if ((maxRegionX - minRegionX + 1) <= maxRegionsPerSide && (maxRegionZ - minRegionZ + 1) <= maxRegionsPerSide) {
            return grupo;
        }

        RegionKey centerRegion = centerPoint == null ? null : new RegionKey(Math.floorDiv(centerPoint.x(), MCARenderer.REGION_BLOCK_SIDE), Math.floorDiv(centerPoint.z(), MCARenderer.REGION_BLOCK_SIDE));
        double anchorX = centerRegion != null ? centerRegion.regionX() : grupo.stream().mapToInt(RegionPreviewCandidate::regionX).average().orElse(0d);
        double anchorZ = centerRegion != null ? centerRegion.regionZ() : grupo.stream().mapToInt(RegionPreviewCandidate::regionZ).average().orElse(0d);

        List<Integer> startXs = construirIniciosVentana(minRegionX, maxRegionX, maxRegionsPerSide, anchorX);
        List<Integer> startZs = construirIniciosVentana(minRegionZ, maxRegionZ, maxRegionsPerSide, anchorZ);

        WindowSelection mejorSeleccion = null;
        for (int startX : startXs) {
            int endX = startX + maxRegionsPerSide - 1;
            for (int startZ : startZs) {
                int endZ = startZ + maxRegionsPerSide - 1;
                List<RegionPreviewCandidate> subset = grupo.stream()
                        .filter(region -> region.regionX() >= startX && region.regionX() <= endX
                                && region.regionZ() >= startZ && region.regionZ() <= endZ)
                        .toList();
                if (subset.isEmpty()) {
                    continue;
                }
                WindowSelection actual = new WindowSelection(startX, startZ, endX, endZ, subset, centerRegion, anchorX, anchorZ);
                if (mejorSeleccion == null || actual.compareTo(mejorSeleccion) < 0) {
                    mejorSeleccion = actual;
                }
            }
        }

        return mejorSeleccion == null ? grupo : mejorSeleccion.regiones();
    }

    private List<Integer> construirIniciosVentana(int minRegion, int maxRegion, int maxRegionsPerSide, double anchor) {
        int span = maxRegion - minRegion + 1;
        if (span <= maxRegionsPerSide) {
            return List.of(minRegion);
        }

        int ultimoInicio = maxRegion - maxRegionsPerSide + 1;
        List<Integer> inicios = new ArrayList<>();
        int sugerido = (int) Math.round(anchor - ((maxRegionsPerSide - 1) / 2.0d));
        sugerido = Math.max(minRegion, Math.min(ultimoInicio, sugerido));
        inicios.add(sugerido);

        for (int current = minRegion; current <= ultimoInicio; current++) {
            if (!inicios.contains(current)) {
                inicios.add(current);
            }
        }
        return inicios;
    }

    private List<RegionPreviewCandidate> elegirMejorGrupo(List<List<RegionPreviewCandidate>> grupos, WorldDataReader.SpawnPoint spawnPoint, MCARenderer.WorldPoint centerPoint) {
        if (grupos == null || grupos.isEmpty()) {
            return List.of();
        }
        if (grupos.size() == 1) {
            return grupos.get(0);
        }

        MCARenderer.WorldPoint effectiveCenter = centerPoint != null
                ? centerPoint
                : (spawnPoint == null ? null : new MCARenderer.WorldPoint(spawnPoint.x(), spawnPoint.z()));
        RegionKey spawnRegion = effectiveCenter == null ? null : new RegionKey(Math.floorDiv(effectiveCenter.x(), 512), Math.floorDiv(effectiveCenter.z(), 512));
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
        Matcher matcher = REGION_FILE_PATTERN.matcher(region.getFileName().toString());
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

    private boolean esArchivoRegionValido(Path region) {
        if (region == null || region.getFileName() == null) {
            return false;
        }
        Matcher matcher = REGION_FILE_PATTERN.matcher(region.getFileName().toString());
        if (!matcher.matches()) {
            return false;
        }
        try {
            Integer.parseInt(matcher.group(1));
            Integer.parseInt(matcher.group(2));
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private record RegionPreviewCandidate(Path path, int regionX, int regionZ, long size) {
        private RegionKey key() {
            return new RegionKey(regionX, regionZ);
        }
    }

    private record RegionKey(int regionX, int regionZ) {}

    private record PreviewRenderLimitOption(String label, int maxPixels) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record PreviewCenterOption(String id, String label, MCARenderer.WorldPoint point) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record WindowSelection(int startX, int startZ, int endX, int endZ, List<RegionPreviewCandidate> regiones,
                                   RegionKey spawnRegion, double anchorX, double anchorZ) {
        private int compareTo(WindowSelection other) {
            boolean thisHasSpawn = contieneSpawn();
            boolean otherHasSpawn = other.contieneSpawn();
            if (thisHasSpawn != otherHasSpawn) {
                return thisHasSpawn ? -1 : 1;
            }

            int byCount = Integer.compare(other.regiones.size(), regiones.size());
            if (byCount != 0) {
                return byCount;
            }

            int byWeight = Long.compare(other.pesoTotal(), pesoTotal());
            if (byWeight != 0) {
                return byWeight;
            }

            int byAnchorDistance = Double.compare(distanciaAlAncla(), other.distanciaAlAncla());
            if (byAnchorDistance != 0) {
                return byAnchorDistance;
            }

            int byZ = Integer.compare(startZ, other.startZ);
            if (byZ != 0) {
                return byZ;
            }
            return Integer.compare(startX, other.startX);
        }

        private boolean contieneSpawn() {
            return spawnRegion != null
                    && spawnRegion.regionX() >= startX && spawnRegion.regionX() <= endX
                    && spawnRegion.regionZ() >= startZ && spawnRegion.regionZ() <= endZ;
        }

        private long pesoTotal() {
            long total = 0L;
            for (RegionPreviewCandidate region : regiones) {
                total += region.size();
            }
            return total;
        }

        private double distanciaAlAncla() {
            double centerX = startX + ((endX - startX) / 2.0d);
            double centerZ = startZ + ((endZ - startZ) / 2.0d);
            return Math.abs(centerX - anchorX) + Math.abs(centerZ - anchorZ);
        }
    }
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
            PreviewOverlayData overlayData = leerPreviewOverlayData(mundo);
            previewImageLabel.setImage(image);
            previewImageLabel.setOverlayData(overlayData);
            previewImageLabel.setChunkGridVisible(limitesChunksEnPreview);
            previewImageLabel.setSpawnVisible(mostrarSpawnEnPreview);
            previewImageLabel.setPlayersVisible(mostrarJugadoresEnPreview);
            if ((limitesChunksEnPreview || mostrarSpawnEnPreview || mostrarJugadoresEnPreview) && overlayData == null) {
                System.out.println("[PanelMundo] Overlay metadata missing for preview. Regenerate the preview once to enable local overlays.");
                JOptionPane.showMessageDialog(
                        this,
                        "Esta preview no tiene metadata de superposición.\nRegenera la preview una vez para poder mostrar el spawn o los límites de chunks.",
                        "Superposiciones no disponibles",
                        JOptionPane.INFORMATION_MESSAGE
                );
            }
        } catch (IOException ex) {
            mostrarTextoPreview("No se ha podido leer la preview.");
        }
    }

    private void mostrarTextoPreview(String texto) {
        previewImageLabel.setMessage(texto);
        previewImageLabel.setOverlayData(null);
        previewImageLabel.setChunkGridVisible(false);
        previewImageLabel.setSpawnVisible(false);
        previewImageLabel.setPlayersVisible(false);
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
        JMenuItem copiarItem = new JMenuItem("Copiar");
        JMenuItem verArchivoItem = new JMenuItem("Ver archivo");
        JMenuItem guardarComoItem = new JMenuItem("Guardar como");
        copiarItem.addActionListener(e -> copiarPreviewAlPortapapeles());
        verArchivoItem.addActionListener(e -> abrirPreviewEnExplorador());
        guardarComoItem.addActionListener(e -> guardarPreviewComo());
        menu.add(copiarItem);
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
                copiarItem.setEnabled(existePreview);
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

    private void copiarPreviewAlPortapapeles() {
        World mundo = getMundoSeleccionadoOActivo();
        if (mundo == null) {
            JOptionPane.showMessageDialog(this, "No hay un mundo seleccionado.", "Copiar", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Path previewPath = getPreviewPath(mundo);
        if (!Files.isRegularFile(previewPath)) {
            JOptionPane.showMessageDialog(this, "La preview todavía no existe.", "Copiar", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            BufferedImage image = ImageIO.read(previewPath.toFile());
            if (image == null) {
                throw new FileNotFoundException("No se ha podido leer la preview.");
            }
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageSelection(image), null);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "No se ha podido copiar la preview: " + ex.getMessage(), "Copiar", JOptionPane.ERROR_MESSAGE);
        }
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

    private Path getPreviewOverlayMetadataPath(World mundo) {
        return Path.of(mundo.getWorldDir()).resolve(PREVIEW_METADATA_FILE);
    }

    private void guardarPreviewOverlayData(World mundo, PreviewOverlayData overlayData) {
        if (mundo == null || overlayData == null) {
            return;
        }

        Properties props = new Properties();
        props.setProperty("originBlockX", Integer.toString(overlayData.originBlockX()));
        props.setProperty("originBlockZ", Integer.toString(overlayData.originBlockZ()));
        props.setProperty("pixelsPerBlock", Integer.toString(overlayData.pixelsPerBlock()));
        if (overlayData.spawnPoint() != null) {
            props.setProperty("spawnX", Integer.toString(overlayData.spawnPoint().x()));
            props.setProperty("spawnZ", Integer.toString(overlayData.spawnPoint().z()));
        }
        List<PreviewPlayerPoint> playerPoints = overlayData.playerPoints();
        int playerIndex = 0;
        if (playerPoints != null) {
            for (PreviewPlayerPoint point : playerPoints) {
                if (point == null || point.username() == null || point.username().isBlank() || point.point() == null) {
                    continue;
                }
                props.setProperty("player." + playerIndex + ".name", point.username());
                props.setProperty("player." + playerIndex + ".x", Integer.toString(point.point().x()));
                props.setProperty("player." + playerIndex + ".z", Integer.toString(point.point().z()));
                playerIndex++;
            }
        }
        props.setProperty("playerCount", Integer.toString(playerIndex));

        Path metadataPath = getPreviewOverlayMetadataPath(mundo);
        try (OutputStream out = Files.newOutputStream(metadataPath)) {
            props.store(out, "Preview overlay metadata");
        } catch (Exception ex) {
            System.out.println("No se ha podido guardar la metadata de overlay de la preview: " + metadataPath);
        }
    }

    private PreviewOverlayData leerPreviewOverlayData(World mundo) {
        if (mundo == null) {
            return null;
        }

        Path metadataPath = getPreviewOverlayMetadataPath(mundo);
        if (!Files.isRegularFile(metadataPath)) {
            return null;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(metadataPath)) {
            props.load(in);
            int originBlockX = Integer.parseInt(props.getProperty("originBlockX"));
            int originBlockZ = Integer.parseInt(props.getProperty("originBlockZ"));
            int pixelsPerBlock = Integer.parseInt(props.getProperty("pixelsPerBlock", "1"));
            String spawnX = props.getProperty("spawnX");
            String spawnZ = props.getProperty("spawnZ");
            MCARenderer.WorldPoint spawnPoint = (spawnX != null && spawnZ != null)
                    ? new MCARenderer.WorldPoint(Integer.parseInt(spawnX), Integer.parseInt(spawnZ))
                    : null;
            int playerCount = Integer.parseInt(props.getProperty("playerCount", "0"));
            List<PreviewPlayerPoint> playerPoints = new ArrayList<>();
            for (int i = 0; i < playerCount; i++) {
                String name = props.getProperty("player." + i + ".name");
                String x = props.getProperty("player." + i + ".x");
                String z = props.getProperty("player." + i + ".z");
                if (name == null || x == null || z == null || name.isBlank()) {
                    continue;
                }
                playerPoints.add(new PreviewPlayerPoint(
                        name,
                        new MCARenderer.WorldPoint(Integer.parseInt(x), Integer.parseInt(z))
                ));
            }
            return new PreviewOverlayData(originBlockX, originBlockZ, pixelsPerBlock, spawnPoint, playerPoints);
        } catch (IOException | NumberFormatException ex) {
            return null;
        }
    }

    private void avisarSuperposicionesNoDisponiblesSiHaceFalta() {
        if (!limitesChunksEnPreview && !mostrarSpawnEnPreview && !mostrarJugadoresEnPreview) {
            return;
        }

        World mundo = getMundoSeleccionadoOActivo();
        if (mundo == null || leerPreviewOverlayData(mundo) != null) {
            return;
        }

        JOptionPane.showMessageDialog(
                this,
                "Esta preview no tiene metadata de superposición.\nRegenera la preview una vez para poder mostrar el spawn o los límites de chunks.",
                "Superposiciones no disponibles",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private void actualizarTextoBotonPreview() {
        if (previewGenerationInProgress) {
            World mundo = getMundoSeleccionadoOActivo();
            if (mismoContextoRenderEnCurso(gestorServidores.getServidorSeleccionado(), mundo)) {
                generarPreviewButton.setText("Cancelar");
            } else {
                generarPreviewButton.setText("Render en curso");
            }
            return;
        }
        World mundo = getMundoSeleccionadoOActivo();
        boolean existePreview = mundo != null && Files.isRegularFile(getPreviewPath(mundo));
        generarPreviewButton.setText(existePreview ? "Regenerar preview" : "Generar preview");
    }

    private void actualizarIndicadorRenderEnCurso() {
        if (!previewGenerationInProgress) {
            previewRenderStatusLabel.setVisible(false);
            previewRenderStatusLabel.setText("");
            previewRenderStatusLabel.setToolTipText(null);
            return;
        }

        String destino = construirEtiquetaRenderEnCurso();
        previewRenderStatusLabel.setText("Renderizando: " + destino);
        previewRenderStatusLabel.setToolTipText(destino);
        previewRenderStatusLabel.setVisible(true);
        previewRenderStatusLabel.revalidate();
        previewRenderStatusLabel.repaint();
    }

    private String construirEtiquetaRenderEnCurso() {
        String serverName = previewGenerationServerName == null || previewGenerationServerName.isBlank()
                ? "Servidor"
                : previewGenerationServerName;
        String worldName = previewGenerationWorldName == null || previewGenerationWorldName.isBlank()
                ? "mundo"
                : previewGenerationWorldName;
        return serverName + " / " + worldName;
    }

    private String construirMensajeRenderEnCurso() {
        return "Ya hay una renderización en curso para " + construirEtiquetaRenderEnCurso()
                + ".\nEspera a que termine o vuelve a ese mundo para cancelarla.";
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

    private void stylePreviewStatusLabel(JLabel label) {
        if (label == null) return;
        label.setVisible(false);
        label.setOpaque(true);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setForeground(Color.WHITE);
        label.setBackground(new Color(24, 28, 36, 220));
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 255, 255, 45)),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
    }

    private void instalarMenuOpcionesPreview() {
        sombreadoMenuItem.setSelected(sombreadoEnPreview);
        sombreadoMenuItem.addActionListener(e -> sombreadoEnPreview = sombreadoMenuItem.isSelected());
        sombreadoAguaMenuItem.setSelected(sombreadoAguaEnPreview);
        sombreadoAguaMenuItem.addActionListener(e -> sombreadoAguaEnPreview = sombreadoAguaMenuItem.isSelected());
        colorearBiomasMenuItem.setSelected(colorearBiomasEnPreview);
        colorearBiomasMenuItem.addActionListener(e -> colorearBiomasEnPreview = colorearBiomasMenuItem.isSelected());
        mostrarSpawnMenuItem.setSelected(mostrarSpawnEnPreview);
        mostrarSpawnMenuItem.addActionListener(e -> {
            mostrarSpawnEnPreview = mostrarSpawnMenuItem.isSelected();
            previewImageLabel.setSpawnVisible(mostrarSpawnEnPreview);
            avisarSuperposicionesNoDisponiblesSiHaceFalta();
        });
        mostrarJugadoresMenuItem.setSelected(mostrarJugadoresEnPreview);
        mostrarJugadoresMenuItem.addActionListener(e -> {
            mostrarJugadoresEnPreview = mostrarJugadoresMenuItem.isSelected();
            previewImageLabel.setPlayersVisible(mostrarJugadoresEnPreview);
            avisarSuperposicionesNoDisponiblesSiHaceFalta();
        });
        limitesChunksMenuItem.setSelected(limitesChunksEnPreview);
        limitesChunksMenuItem.addActionListener(e -> {
            limitesChunksEnPreview = limitesChunksMenuItem.isSelected();
            previewImageLabel.setChunkGridVisible(limitesChunksEnPreview);
            avisarSuperposicionesNoDisponiblesSiHaceFalta();
        });
        usarTodoMenuItem.setSelected(usarTodoEnPreview);
        usarTodoMenuItem.addActionListener(e -> {
            usarTodoEnPreview = usarTodoMenuItem.isSelected();
            actualizarEstadoControlesRender();
        });
        configurarLimiteRenderCombo();
        configurarCentroRenderCombo();
        actualizarEstadoControlesRender();

        JPanel optionsPanel = new JPanel();
        optionsPanel.setOpaque(true);
        optionsPanel.setBackground(AppTheme.getBackground());
        optionsPanel.setBorder(AppTheme.createRoundedBorder(new Insets(6, 6, 6, 6), 1f));
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));

        stylePreviewOptionCheckBox(mostrarSpawnMenuItem);
        stylePreviewOptionCheckBox(usarTodoMenuItem);
        stylePreviewOptionCheckBox(sombreadoMenuItem);
        stylePreviewOptionCheckBox(sombreadoAguaMenuItem);
        stylePreviewOptionCheckBox(colorearBiomasMenuItem);
        stylePreviewOptionCheckBox(mostrarJugadoresMenuItem);
        stylePreviewOptionCheckBox(limitesChunksMenuItem);
        optionsPanel.add(createPreviewOptionsSectionLabel("Generación"));
        optionsPanel.add(createPreviewOptionRow("Límite render", limiteRenderCombo));
        optionsPanel.add(Box.createVerticalStrut(8));
        optionsPanel.add(createPreviewOptionRow("Centro render", centroRenderCombo));
        optionsPanel.add(Box.createVerticalStrut(8));
        optionsPanel.add(sombreadoMenuItem);
        optionsPanel.add(Box.createVerticalStrut(4));
        optionsPanel.add(sombreadoAguaMenuItem);
        optionsPanel.add(Box.createVerticalStrut(4));
        optionsPanel.add(colorearBiomasMenuItem);
        optionsPanel.add(Box.createVerticalStrut(4));
        optionsPanel.add(usarTodoMenuItem);
        optionsPanel.add(Box.createVerticalStrut(10));
        optionsPanel.add(createPreviewOptionsSectionLabel("Superposición"));
        optionsPanel.add(mostrarSpawnMenuItem);
        optionsPanel.add(Box.createVerticalStrut(4));
        optionsPanel.add(mostrarJugadoresMenuItem);
        optionsPanel.add(Box.createVerticalStrut(4));
        optionsPanel.add(limitesChunksMenuItem);

        previewOptionsPanel = optionsPanel;

        previewMenuButton.addActionListener(e -> mostrarMenuOpcionesPreview(previewMenuButton));
    }

    private void mostrarMenuOpcionesPreview(Component anchor) {
        if (anchor == null) {
            return;
        }
        sombreadoMenuItem.setSelected(sombreadoEnPreview);
        sombreadoAguaMenuItem.setSelected(sombreadoAguaEnPreview);
        colorearBiomasMenuItem.setSelected(colorearBiomasEnPreview);
        mostrarSpawnMenuItem.setSelected(mostrarSpawnEnPreview);
        mostrarJugadoresMenuItem.setSelected(mostrarJugadoresEnPreview);
        limitesChunksMenuItem.setSelected(limitesChunksEnPreview);
        usarTodoMenuItem.setSelected(usarTodoEnPreview);
        actualizarEstadoControlesRender();
        seleccionarLimiteRenderComboActual();
        actualizarCentroRenderCombo();
        if (previewOptionsDialog != null && previewOptionsDialog.isVisible()) {
            previewOptionsDialog.setVisible(false);
            previewOptionsDialog.dispose();
            previewOptionsDialog = null;
            return;
        }

        Window owner = SwingUtilities.getWindowAncestor(this);
        if (owner == null || previewOptionsPanel == null) {
            return;
        }

        JDialog dialog = new JDialog(owner);
        dialog.setUndecorated(true);
        dialog.setModal(false);
        dialog.setAlwaysOnTop(false);
        dialog.setFocusableWindowState(true);
        dialog.setBackground(new Color(0, 0, 0, 0));
        dialog.setContentPane(previewOptionsPanel);
        dialog.pack();

        Point screenLocation = anchor.getLocationOnScreen();
        int x = screenLocation.x + anchor.getWidth() - dialog.getWidth();
        int y = screenLocation.y + anchor.getHeight();
        dialog.setLocation(x, y);
        dialog.addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowLostFocus(java.awt.event.WindowEvent e) {
                dialog.setVisible(false);
                dialog.dispose();
                if (previewOptionsDialog == dialog) {
                    previewOptionsDialog = null;
                }
            }
        });

        previewOptionsDialog = dialog;
        dialog.setVisible(true);
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

    private void configurarLimiteRenderCombo() {
        limiteRenderCombo.setOpaque(false);
        limiteRenderCombo.setFocusable(false);
        limiteRenderCombo.putClientProperty("JComponent.roundRect", true);
        limiteRenderCombo.setMaximumSize(new Dimension(180, 30));
        limiteRenderCombo.setPreferredSize(new Dimension(180, 30));
        limiteRenderCombo.addActionListener(e -> {
            PreviewRenderLimitOption selected = (PreviewRenderLimitOption) limiteRenderCombo.getSelectedItem();
            if (selected != null) {
                limiteMaximoRenderPreview = selected.maxPixels();
            }
        });
        seleccionarLimiteRenderComboActual();
    }

    private void seleccionarLimiteRenderComboActual() {
        for (int i = 0; i < limiteRenderCombo.getItemCount(); i++) {
            PreviewRenderLimitOption option = limiteRenderCombo.getItemAt(i);
            if (option.maxPixels() == limiteMaximoRenderPreview) {
                limiteRenderCombo.setSelectedIndex(i);
                return;
            }
        }
        if (limiteRenderCombo.getItemCount() > 0) {
            limiteRenderCombo.setSelectedIndex(0);
        }
    }

    private void configurarCentroRenderCombo() {
        centroRenderCombo.setOpaque(false);
        centroRenderCombo.setFocusable(false);
        centroRenderCombo.putClientProperty("JComponent.roundRect", true);
        centroRenderCombo.setMaximumSize(new Dimension(180, 30));
        centroRenderCombo.setPreferredSize(new Dimension(180, 30));
        centroRenderCombo.addActionListener(e -> {
            PreviewCenterOption selected = (PreviewCenterOption) centroRenderCombo.getSelectedItem();
            if (selected != null) {
                centroRenderPreviewId = selected.id();
            }
        });
    }

    private void actualizarCentroRenderCombo() {
        World mundo = getMundoSeleccionadoOActivo();
        Server server = gestorServidores.getServidorSeleccionado();
        WorldDataReader.SpawnPoint spawnPoint = mundo == null ? null : WorldDataReader.getSpawnPoint(mundo);
        List<PreviewPlayerData> recentPlayers = obtenerJugadoresRecientesDesdePlayerdata(server, mundo);

        DefaultComboBoxModel<PreviewCenterOption> model = new DefaultComboBoxModel<>();
        if (spawnPoint != null) {
            model.addElement(new PreviewCenterOption("spawn", "Spawn", new MCARenderer.WorldPoint(spawnPoint.x(), spawnPoint.z())));
        }
        for (PreviewPlayerData player : recentPlayers) {
            String label = (player.username() == null || player.username().isBlank()) ? "Jugador" : player.username();
            model.addElement(new PreviewCenterOption("player:" + player.username(), label, player.point()));
        }

        centroRenderCombo.setModel(model);
        seleccionarCentroRenderComboActual();
    }

    private void seleccionarCentroRenderComboActual() {
        for (int i = 0; i < centroRenderCombo.getItemCount(); i++) {
            PreviewCenterOption option = centroRenderCombo.getItemAt(i);
            if (Objects.equals(option.id(), centroRenderPreviewId)) {
                centroRenderCombo.setSelectedIndex(i);
                return;
            }
        }
        if (centroRenderCombo.getItemCount() > 0) {
            centroRenderCombo.setSelectedIndex(0);
            PreviewCenterOption selected = (PreviewCenterOption) centroRenderCombo.getSelectedItem();
            centroRenderPreviewId = selected == null ? "spawn" : selected.id();
        }
    }

    private void actualizarEstadoControlesRender() {
        boolean habilitados = !usarTodoEnPreview;
        limiteRenderCombo.setEnabled(habilitados);
        centroRenderCombo.setEnabled(habilitados);
    }

    private MCARenderer.WorldPoint resolverCentroRender(World mundo, WorldDataReader.SpawnPoint spawnPoint, List<PreviewPlayerData> recentPlayers) {
        if (centroRenderPreviewId == null || centroRenderPreviewId.isBlank() || "spawn".equals(centroRenderPreviewId)) {
            return spawnPoint == null ? null : new MCARenderer.WorldPoint(spawnPoint.x(), spawnPoint.z());
        }

        if (recentPlayers != null) {
            for (PreviewPlayerData player : recentPlayers) {
                if (player == null || player.username() == null || player.username().isBlank()) {
                    continue;
                }
                if (Objects.equals("player:" + player.username(), centroRenderPreviewId)) {
                    return player.point();
                }
            }
        }

        return spawnPoint == null ? null : new MCARenderer.WorldPoint(spawnPoint.x(), spawnPoint.z());
    }

    private JPanel createPreviewOptionRow(String label, JComponent component) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel textLabel = new JLabel(label);
        textLabel.setForeground(AppTheme.getForeground());
        row.add(textLabel, BorderLayout.WEST);
        row.add(component, BorderLayout.EAST);
        return row;
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

    private static String obtenerInicial(String username) {
        if (username == null || username.isBlank()) return "?";
        return username.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private static void precargarCabezasJugadoresPreview(List<PreviewPlayerPoint> playerPoints, Runnable onLoaded) {
        if (playerPoints == null || playerPoints.isEmpty()) {
            return;
        }
        for (PreviewPlayerPoint playerPoint : playerPoints) {
            if (playerPoint == null || playerPoint.username() == null || playerPoint.username().isBlank()) {
                continue;
            }
            cargarCabezaJugadorPreviewAsync(playerPoint.username(), onLoaded);
        }
    }

    private static void cargarCabezaJugadorPreviewAsync(String username, Runnable onLoaded) {
        if (username == null || username.isBlank()) {
            return;
        }

        String key = username.strip().toLowerCase(Locale.ROOT);
        if (key.isBlank() || PLAYER_HEAD_CACHE.containsKey(key) || !PLAYER_HEADS_LOADING.add(key)) {
            return;
        }

        new Thread(() -> {
            try {
                ImageIcon head = new MojangAPI().obtenerCabezaJugador(username, PREVIEW_PLAYER_HEAD_SIZE);
                if (head != null) {
                    PLAYER_HEAD_CACHE.put(key, head);
                }
            } finally {
                PLAYER_HEADS_LOADING.remove(key);
                if (onLoaded != null) {
                    SwingUtilities.invokeLater(onLoaded);
                }
            }
        }, "preview-head-" + key).start();
    }

    private static final class PreviewImagePanel extends JComponent {
        private BufferedImage image;
        private PreviewOverlayData overlayData;
        private boolean chunkGridVisible = false;
        private boolean spawnVisible = false;
        private boolean playersVisible = false;
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

        private void setOverlayData(PreviewOverlayData overlayData) {
            this.overlayData = overlayData;
            precargarCabezasJugadoresPreview(overlayData == null ? null : overlayData.playerPoints(), this::repaint);
            repaint();
        }

        private void setChunkGridVisible(boolean chunkGridVisible) {
            this.chunkGridVisible = chunkGridVisible;
            repaint();
        }

        private void setSpawnVisible(boolean spawnVisible) {
            this.spawnVisible = spawnVisible;
            repaint();
        }

        private void setPlayersVisible(boolean playersVisible) {
            this.playersVisible = playersVisible;
            if (playersVisible && overlayData != null) {
                precargarCabezasJugadoresPreview(overlayData.playerPoints(), this::repaint);
            }
            repaint();
        }

        private void clearImage() {
            this.image = null;
            this.overlayData = null;
            this.message = "";
            resetView();
            updateCursor();
        }

        private void setMessage(String message) {
            this.image = null;
            this.overlayData = null;
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
                paintChunkGrid(g2, state);
                paintSpawn(g2, state);
                paintPlayers(g2, state);
            } finally {
                g2.dispose();
            }
        }

        private void paintChunkGrid(Graphics2D g2, RenderState state) {
            if (!chunkGridVisible || overlayData == null || state == null) {
                return;
            }

            double pixelsPerChunk = overlayData.pixelsPerBlock() * 16d * state.scale();
            if (pixelsPerChunk < 6d) {
                return;
            }

            double firstLineX = state.drawX() - (floorMod(overlayData.originBlockX(), 16) * overlayData.pixelsPerBlock() * state.scale());
            double firstLineY = state.drawY() - (floorMod(overlayData.originBlockZ(), 16) * overlayData.pixelsPerBlock() * state.scale());

            Graphics2D grid = (Graphics2D) g2.create();
            try {
                grid.setClip(state.drawX(), state.drawY(), state.drawWidth(), state.drawHeight());
                grid.setColor(new Color(255, 255, 255, 72));

                for (double x = firstLineX; x <= state.drawX() + state.drawWidth(); x += pixelsPerChunk) {
                    int xi = (int) Math.round(x);
                    grid.drawLine(xi, state.drawY(), xi, state.drawY() + state.drawHeight());
                }
                for (double y = firstLineY; y <= state.drawY() + state.drawHeight(); y += pixelsPerChunk) {
                    int yi = (int) Math.round(y);
                    grid.drawLine(state.drawX(), yi, state.drawX() + state.drawWidth(), yi);
                }
            } finally {
                grid.dispose();
            }
        }

        private int floorMod(int value, int mod) {
            int result = value % mod;
            return result < 0 ? result + mod : result;
        }

        private void paintSpawn(Graphics2D g2, RenderState state) {
            if (!spawnVisible || overlayData == null || overlayData.spawnPoint() == null || state == null) {
                return;
            }

            double relativeBlockX = overlayData.spawnPoint().x() - overlayData.originBlockX();
            double relativeBlockZ = overlayData.spawnPoint().z() - overlayData.originBlockZ();
            double imagePixelX = relativeBlockX * overlayData.pixelsPerBlock();
            double imagePixelY = relativeBlockZ * overlayData.pixelsPerBlock();
            int centerX = (int) Math.round(state.drawX() + imagePixelX * state.scale());
            int centerY = (int) Math.round(state.drawY() + imagePixelY * state.scale());

            if (centerX < state.drawX() || centerY < state.drawY()
                    || centerX >= state.drawX() + state.drawWidth()
                    || centerY >= state.drawY() + state.drawHeight()) {
                return;
            }

            Graphics2D marker = (Graphics2D) g2.create();
            try {
                marker.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int arm = 10;
                int shadowThickness = 4;
                int mainThickness = 2;

                marker.setColor(new Color(0, 0, 0, 120));
                marker.setStroke(new BasicStroke(shadowThickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                marker.drawLine(centerX - arm, centerY - arm, centerX + arm, centerY + arm);
                marker.drawLine(centerX - arm, centerY + arm, centerX + arm, centerY - arm);

                marker.setColor(new Color(225, 60, 60, 245));
                marker.setStroke(new BasicStroke(mainThickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                marker.drawLine(centerX - arm, centerY - arm, centerX + arm, centerY + arm);
                marker.drawLine(centerX - arm, centerY + arm, centerX + arm, centerY - arm);
            } finally {
                marker.dispose();
            }
        }

        private void paintPlayers(Graphics2D g2, RenderState state) {
            if (!playersVisible || overlayData == null || overlayData.playerPoints() == null || overlayData.playerPoints().isEmpty() || state == null) {
                return;
            }

            Graphics2D marker = (Graphics2D) g2.create();
            try {
                marker.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                marker.setFont(marker.getFont().deriveFont(Font.BOLD, 11f));

                for (PreviewPlayerPoint player : overlayData.playerPoints()) {
                    if (player == null || player.point() == null || player.username() == null || player.username().isBlank()) {
                        continue;
                    }

                    double relativeBlockX = player.point().x() - overlayData.originBlockX();
                    double relativeBlockZ = player.point().z() - overlayData.originBlockZ();
                    double imagePixelX = relativeBlockX * overlayData.pixelsPerBlock();
                    double imagePixelY = relativeBlockZ * overlayData.pixelsPerBlock();
                    int centerX = (int) Math.round(state.drawX() + imagePixelX * state.scale());
                    int centerY = (int) Math.round(state.drawY() + imagePixelY * state.scale());

                    if (centerX < state.drawX() || centerY < state.drawY()
                            || centerX >= state.drawX() + state.drawWidth()
                            || centerY >= state.drawY() + state.drawHeight()) {
                        continue;
                    }

                    String cacheKey = player.username().strip().toLowerCase(Locale.ROOT);
                    ImageIcon headIcon = PLAYER_HEAD_CACHE.get(cacheKey);
                    if (headIcon != null && headIcon.getImage() != null) {
                        int headSize = PREVIEW_PLAYER_HEAD_SIZE;
                        int headX = centerX - (headSize / 2);
                        int headY = centerY - (headSize / 2);
                        marker.setColor(new Color(0, 0, 0, 130));
                        marker.fillRoundRect(headX - 2, headY - 2, headSize + 4, headSize + 4, 6, 6);
                        marker.drawImage(headIcon.getImage(), headX, headY, null);
                    } else {
                        marker.setColor(new Color(0, 0, 0, 120));
                        marker.fillOval(centerX - 6, centerY - 6, 14, 14);
                        marker.setColor(new Color(255, 212, 74, 240));
                        marker.fillOval(centerX - 5, centerY - 5, 12, 12);
                        marker.setColor(new Color(50, 35, 12, 220));
                        marker.drawString(obtenerInicial(player.username()), centerX - 3, centerY + 4);
                    }

                    String label = player.username().length() > 10 ? player.username().substring(0, 10) : player.username();
                    FontMetrics metrics = marker.getFontMetrics();
                    int textX = centerX + 9;
                    int textY = centerY - 8;
                    int textWidth = metrics.stringWidth(label);
                    marker.setColor(new Color(0, 0, 0, 150));
                    marker.fillRoundRect(textX - 3, textY - metrics.getAscent(), textWidth + 6, metrics.getHeight(), 8, 8);
                    marker.setColor(new Color(255, 244, 204));
                    marker.drawString(label, textX, textY);
                }
            } finally {
                marker.dispose();
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

    private record RecentConnection(String username, String timestamp, String location, long sortEpochMillis) {
        private RecentConnection(String username, String timestamp, String location) {
            this(username, timestamp, location, 0L);
        }
    }

    private record PreviewGenerationResult(Path outputPath, boolean sinRegiones, BufferedImage preview, PreviewOverlayData overlayData) {}
    private record PreviewOverlayData(int originBlockX, int originBlockZ, int pixelsPerBlock,
                                      MCARenderer.WorldPoint spawnPoint, List<PreviewPlayerPoint> playerPoints) {}
    private record PreviewPlayerPoint(String username, MCARenderer.WorldPoint point) {}
    private record PreviewPlayerData(String username, MCARenderer.WorldPoint point, FileTime lastSeen) {}
    private record CroppedPreview(BufferedImage image, int originBlockX, int originBlockZ) {}
    private record RenderWorldBounds(int minBlockX, int maxBlockX, int minBlockZ, int maxBlockZ) {}

    private static final class ImageSelection implements Transferable {
        private final Image image;

        private ImageSelection(Image image) {
            this.image = image;
        }

        @Override
        public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
            return new java.awt.datatransfer.DataFlavor[]{java.awt.datatransfer.DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) {
            return java.awt.datatransfer.DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(java.awt.datatransfer.DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return image;
        }
    }
}
