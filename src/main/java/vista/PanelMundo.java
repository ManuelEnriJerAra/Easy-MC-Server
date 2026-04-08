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
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class PanelMundo extends JPanel {
    private static final Pattern JOIN = Pattern.compile("([^\\s]+) joined the game");
    private static final Pattern HORA_LOG = Pattern.compile("^\\[(\\d{2}:\\d{2}:\\d{2})]\\s*");
    private static final DateTimeFormatter FORMATO_HORA_LOG = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATO_CONEXION = DateTimeFormatter.ofPattern("dd/MM/yyyy - HH:mm:ss");
    private static final String WORLD_METADATA_FILE = ".emw-world.properties";

    private final GestorServidores gestorServidores;
    private final Runnable onWorldChanged;

    private final JLabel tiempoRealLabel = new JLabel("-");
    private final JLabel lastPlayedLabel = new JLabel("Última apertura: -");
    private final JLabel versionLabel = new JLabel("Versión: -");
    private final JLabel tipoMundoLabel = new JLabel("-");
    private final JLabel previewImageLabel = new JLabel("La preview todavía no existe.", SwingConstants.CENTER);
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
    private final JLabel pesoMundoLabel = new JLabel("-");
    private final JLabel pesoStatsSavesLabel = new JLabel("Peso stats y saves: -");
    private final JLabel pesoTotalLabel = new JLabel("-");
    private final JLabel pesoMundoValueLabel = new JLabel("-");
    private final JLabel pesoStatsSavesValueLabel = new JLabel("-");
    private final JLabel pesoTotalValueLabel = new JLabel("-");
    private final JPanel conexionesPanel = new JPanel();

    private World mundoActivoActual;
    private String mundoTicksCache = "";
    private long ticksMostradosCache = 0L;
    private long ticksArchivoCache = 0L;
    private long ultimaActualizacionTicksMs = 0L;
    private boolean actualizandoComboMundos = false;

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
        applyDefaultPrimaryButtonStyle();

        previewImageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        previewImageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        previewImageLabel.setVerticalAlignment(SwingConstants.CENTER);
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

        JPanel overlayBadge = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(AppTheme.withAlpha(AppTheme.getSelectionBackground(), 110));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                } finally {
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        overlayBadge.setOpaque(false);
        overlayBadge.add(generarPreviewButton, BorderLayout.CENTER);

        JPanel overlayCorner = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        overlayCorner.setOpaque(false);
        overlayCorner.add(overlayBadge);

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
        mundoTicksCache = "";
        ticksMostradosCache = 0L;
        ticksArchivoCache = 0L;
        ultimaActualizacionTicksMs = System.currentTimeMillis();

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

        long ahoraMs = System.currentTimeMillis();
        long ticksArchivo = WorldDataReader.getActiveTicks(mundo);
        String claveMundo = mundo.getWorldName();
        boolean esMismoMundo = Objects.equals(claveMundo, mundoTicksCache);
        boolean servidorActivo = server.getServerProcess() != null && server.getServerProcess().isAlive();

        if (!esMismoMundo) {
            mundoTicksCache = claveMundo;
            ticksArchivoCache = ticksArchivo;
            ticksMostradosCache = ticksArchivo;
            ultimaActualizacionTicksMs = ahoraMs;
        } else if (ticksArchivo > ticksArchivoCache) {
            ticksArchivoCache = ticksArchivo;
            ticksMostradosCache = ticksArchivo;
            ultimaActualizacionTicksMs = ahoraMs;
        } else if (servidorActivo) {
            long deltaMs = Math.max(0L, ahoraMs - ultimaActualizacionTicksMs);
            long deltaTicksEstimados = (deltaMs * 20L) / 1000L;
            if (deltaTicksEstimados > 0L) {
                ticksMostradosCache += deltaTicksEstimados;
                ultimaActualizacionTicksMs = ahoraMs;
            }
        } else {
            ticksMostradosCache = ticksArchivo;
            ticksArchivoCache = ticksArchivo;
            ultimaActualizacionTicksMs = ahoraMs;
        }

        tiempoRealValueLabel.setText(formatearTiempo(Math.max(ticksMostradosCache, 0L)));
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
        previewImageLabel.setIcon(null);
        previewImageLabel.setText("");
        setPreviewProgressVisible(true);
        generarPreviewButton.setEnabled(false);
        MCARenderer renderer = new MCARenderer();
        WorldDataReader.SpawnPoint spawnPoint = null;
        SwingWorker<PreviewGenerationResult, Void> worker = new SwingWorker<>() {
            @Override
            protected PreviewGenerationResult doInBackground() throws Exception {
                List<Path> regionesPreview = encontrarRegionesPreview(mundo, false);
                if (regionesPreview.isEmpty()) {
                    return new PreviewGenerationResult(outputPath, true);
                }
                MCARenderer.WorldPoint markerPoint = spawnPoint == null
                        ? null
                        : new MCARenderer.WorldPoint(spawnPoint.x(), spawnPoint.z());
                BufferedImage preview = renderer.renderWorld(regionesPreview, MCARenderer.RenderOptions.defaults(), markerPoint);
                guardarPreview(preview, outputPath);
                return new PreviewGenerationResult(outputPath, false);
            }

            @Override
            protected void done() {
                updateUseWorldButtonState();
                setPreviewProgressVisible(false);
                generarPreviewButton.setEnabled(mundosCombo.isEnabled() && getMundoSeleccionadoOActivo() != null);
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

    private List<Path> encontrarRegionesPreview(World mundo, boolean generarTodo) throws Exception {
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
                return regiones;
            }

            List<Path> visibles = new java.util.ArrayList<>();
            for (Path region : regiones) {
                if (renderer.hasVisibleBlocks(region)) {
                    visibles.add(region);
                }
            }
            return visibles.isEmpty() ? regiones : visibles;
        }
    }

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
            Image scaled = escalarPreview(image, 320, 320);
            previewImageLabel.setText("");
            previewImageLabel.setIcon(new ImageIcon(scaled));
        } catch (IOException ex) {
            mostrarTextoPreview("No se ha podido leer la preview.");
        }
    }

    private void mostrarTextoPreview(String texto) {
        previewImageLabel.setIcon(null);
        previewImageLabel.setText(texto);
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
        verArchivoItem.addActionListener(e -> abrirPreviewEnExplorador());
        menu.add(verArchivoItem);

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
                verArchivoItem.setEnabled(existePreviewSeleccionada());
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

    private Image escalarPreview(BufferedImage image, int maxWidth, int maxHeight) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) return image;

        double ratio = Math.min((double) maxWidth / width, (double) maxHeight / height);
        ratio = Math.min(1.0d, ratio);
        int scaledWidth = Math.max(1, (int) Math.round(width * ratio));
        int scaledHeight = Math.max(1, (int) Math.round(height * ratio));
        return image.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
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
