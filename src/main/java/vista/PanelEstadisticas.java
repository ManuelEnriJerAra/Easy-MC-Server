package vista;

import controlador.GestorConfiguracion;
import controlador.GestorServidores;
import modelo.Server;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.ObjectMapper;

public class PanelEstadisticas extends JPanel {
    private static final int SAMPLE_INTERVAL_MS = 1000;
    private static final int CHART_LEFT_PADDING = 52;
    private static final int CHART_RIGHT_PADDING = 92;
    private static final int CHART_TOP_PADDING = 28;
    private static final int CHART_BOTTOM_PADDING = 28;
    private static final Pattern PLAYER_JOIN = Pattern.compile("([^\\s]+) joined the game");
    private static final Pattern PLAYER_LEFT = Pattern.compile("([^\\s]+) left the game");
    private static final int[] RECENT_WINDOW_OPTIONS = {
            30 * 24 * 60 * 60,
            60 * 24 * 60 * 60,
            90 * 24 * 60 * 60,
            120 * 24 * 60 * 60,
            150 * 24 * 60 * 60,
            180 * 24 * 60 * 60,
            210 * 24 * 60 * 60,
            240 * 24 * 60 * 60,
            270 * 24 * 60 * 60,
            300 * 24 * 60 * 60,
            330 * 24 * 60 * 60,
            360 * 24 * 60 * 60
    };
    private static final int[] HISTORICAL_RESOLUTION_OPTIONS = {
            60,
            30 * 60,
            60 * 60,
            6 * 60 * 60,
            12 * 60 * 60,
            24 * 60 * 60
    };
    private static final DateTimeFormatter SAMPLE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final Map<String, StatsHistory> HISTORY_BY_SERVER = new ConcurrentHashMap<>();
    private static final Set<GestorServidores> REGISTERED_GESTORES = ConcurrentHashMap.newKeySet();
    private static final ObjectMapper HISTORY_MAPPER = new ObjectMapper();
    private static final ScheduledExecutorService BACKGROUND_SAMPLER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "stats-background-sampler");
        thread.setDaemon(true);
        return thread;
    });
    private static final ScheduledExecutorService HISTORY_SAVER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "stats-history-saver");
        thread.setDaemon(true);
        return thread;
    });

    static {
        BACKGROUND_SAMPLER.scheduleAtFixedRate(PanelEstadisticas::sampleActiveServers, 0, SAMPLE_INTERVAL_MS, TimeUnit.MILLISECONDS);
        HISTORY_SAVER.scheduleAtFixedRate(PanelEstadisticas::flushDirtyHistories, 15, 15, TimeUnit.SECONDS);
    }

    private final GestorServidores gestorServidores;
    private final Server server;
    private final StatsHistory statsHistory;
    private final JLabel ramActualValueLabel = new JLabel("-");
    private final JLabel discoActivoValueLabel = new JLabel("-");
    private final JLabel estadoValueLabel = new JLabel("Sin datos");
    private final JComboBox<TimeRangeOption> rangoCombo = new JComboBox<>(TimeRangeOption.values());
    private final JButton exportarGraficasButton = new JButton("Exportar gráficas");
    private final JButton ajustesHistoricoButton = new JButton("Configuración");
    private final JLabel posicionHistoricoLabel = new JLabel("Ventana actual");
    private final JCheckBox persistenciaCheckBox = new JCheckBox("Persistencia activa");
    private final JSlider ventanaRecienteSlider = new JSlider();
    private final JSlider resolucionHistoricaSlider = new JSlider();
    private final JCheckBox ramActivaCheckBox = new JCheckBox();
    private final JCheckBox ramPersistenciaCheckBox = new JCheckBox();
    private final JCheckBox discoActivaCheckBox = new JCheckBox();
    private final JCheckBox discoPersistenciaCheckBox = new JCheckBox();
    private final JCheckBox jugadoresActivaCheckBox = new JCheckBox();
    private final JCheckBox jugadoresPersistenciaCheckBox = new JCheckBox();
    private final JButton reiniciarHistoricoButton = new JButton("Reiniciar");
    private final JButton guardarHistoricoButton = new JButton("Guardar");
    private final JLabel ventanaRecienteValueLabel = new JLabel();
    private final JLabel resolucionHistoricaValueLabel = new JLabel();
    private final UsageChart ramChart = new UsageChart("RAM", UsageMode.MEGABYTES, "Esperando muestras de RAM...");
    private final UsageChart diskChart = new UsageChart("Disco", UsageMode.PERCENT, "Esperando actividad de disco...");
    private final UsageChart playersChart = new UsageChart("Jugadores", UsageMode.COUNT, "Esperando actividad de jugadores...");
    private final JPanel chartsPanel = new JPanel();
    private final JScrollPane chartsScrollPane = new JScrollPane();
    private final JScrollBar historialScrollBar = new JScrollBar(JScrollBar.HORIZONTAL);
    private Timer refreshTimer;
    private final PropertyChangeListener estadoServidorListener;
    private boolean estadoListenerRegistrado = false;
    private final int originalTooltipInitialDelay;
    private final int originalTooltipReshowDelay;
    private final int originalTooltipDismissDelay;
    private boolean updatingSettingsControls = false;
    private boolean updatingTimelineControls = false;
    private int timelineMaxOffsetSeconds = 0;
    private boolean persistedStatsPersistenceEnabled = true;
    private int persistedStatsRecentWindowSeconds = 30 * 24 * 60 * 60;
    private int persistedStatsHistoricalResolutionSeconds = 60;
    private final EnumMap<StatsChartOption, Boolean> pendingChartEnabled = new EnumMap<>(StatsChartOption.class);
    private final EnumMap<StatsChartOption, Boolean> pendingChartPersistenceEnabled = new EnumMap<>(StatsChartOption.class);
    private final EnumMap<StatsChartOption, Boolean> persistedChartEnabled = new EnumMap<>(StatsChartOption.class);
    private final EnumMap<StatsChartOption, Boolean> persistedChartPersistenceEnabled = new EnumMap<>(StatsChartOption.class);

    PanelEstadisticas(GestorServidores gestorServidores, Server server) {
        this.gestorServidores = gestorServidores;
        this.server = server;
        this.statsHistory = getOrCreateHistory(server);
        ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
        originalTooltipInitialDelay = toolTipManager.getInitialDelay();
        originalTooltipReshowDelay = toolTipManager.getReshowDelay();
        originalTooltipDismissDelay = toolTipManager.getDismissDelay();

        setLayout(new BorderLayout());
        setOpaque(false);

        TitledCardPanel card = new TitledCardPanel("Estadísticas", new Insets(12, 12, 12, 12));
        card.setBorder(BorderFactory.createEmptyBorder());
        card.getContentPanel().add(crearContenido(), BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);

        rangoCombo.setSelectedItem(TimeRangeOption.fromSeconds(getStatsRangeSeconds()));
        rangoCombo.addActionListener(e -> {
            TimeRangeOption selectedRange = getSelectedRange();
            if (server != null) {
                server.setEstadisticasRangoSegundos(selectedRange.seconds());
                guardarConfiguracionServidor();
            }
            refrescarGraficasDesdeHistorial();
        });
        ajustesHistoricoButton.setFocusable(false);
        exportarGraficasButton.setFocusable(false);
        exportarGraficasButton.addActionListener(e -> abrirDialogoExportacion());
        styleActionButton(exportarGraficasButton);
        ajustesHistoricoButton.addActionListener(e -> abrirDialogoHistorico());
        styleActionButton(ajustesHistoricoButton);
        styleActionButton(reiniciarHistoricoButton);
        styleActionButton(guardarHistoricoButton);
        historialScrollBar.setFocusable(false);
        historialScrollBar.setUnitIncrement(60);
        historialScrollBar.addAdjustmentListener(e -> {
            if (updatingTimelineControls) {
                return;
            }
            refrescarGraficasDesdeHistorial();
        });
        posicionHistoricoLabel.setForeground(AppTheme.getMutedForeground());
        instalarNavegacionConRueda(ramChart);
        instalarNavegacionConRueda(diskChart);
        instalarNavegacionConRueda(playersChart);
        instalarNavegacionConRueda(historialScrollBar);
        instalarNavegacionConRueda(posicionHistoricoLabel);
        chartsScrollPane.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                updateChartHeights();
            }
        });
        configurarControlesHistorico();

        estadoServidorListener = evt -> {
            if (!"estadoServidor".equals(evt.getPropertyName())) {
                return;
            }
            Object value = evt.getNewValue();
            if (!(value instanceof Server updatedServer) || !esMismoServidor(updatedServer)) {
                return;
            }
            SwingUtilities.invokeLater(this::solicitarMuestras);
        };
        iniciarRecursosSiHaceFalta();
        refrescarGraficasDesdeHistorial();
        SwingUtilities.invokeLater(this::solicitarMuestras);
    }

    private JPanel crearContenido() {
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setOpaque(false);

        JPanel header = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        header.setOpaque(false);

        JPanel rangoPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rangoPanel.setOpaque(false);
        JLabel rangoLabel = new JLabel("Rango: ");
        rangoLabel.setForeground(AppTheme.getMutedForeground());
        rangoPanel.add(rangoLabel);
        rangoCombo.setFocusable(false);
        rangoPanel.add(rangoCombo);
        rangoPanel.add(Box.createHorizontalStrut(8));
        rangoPanel.add(exportarGraficasButton);
        rangoPanel.add(Box.createHorizontalStrut(8));
        rangoPanel.add(crearSeparadorVertical(24));
        rangoPanel.add(Box.createHorizontalStrut(8));
        rangoPanel.add(ajustesHistoricoButton);
        header.add(rangoPanel);
        content.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);
        center.add(crearResumen(), BorderLayout.NORTH);

        chartsPanel.setOpaque(false);
        chartsPanel.setLayout(new BoxLayout(chartsPanel, BoxLayout.Y_AXIS));
        rebuildChartsPanel();

        chartsScrollPane.setBorder(BorderFactory.createEmptyBorder());
        chartsScrollPane.setOpaque(false);
        chartsScrollPane.getViewport().setOpaque(false);
        chartsScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        chartsScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        chartsScrollPane.getVerticalScrollBar().setUnitIncrement(24);
        chartsScrollPane.setViewportView(chartsPanel);

        center.add(chartsScrollPane, BorderLayout.CENTER);
        center.add(crearNavegacionHistorico(), BorderLayout.SOUTH);
        content.add(center, BorderLayout.CENTER);
        return content;
    }

    private JPanel crearNavegacionHistorico() {
        JPanel navigation = new JPanel(new BorderLayout(8, 0));
        navigation.setOpaque(false);
        navigation.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        posicionHistoricoLabel.setPreferredSize(new Dimension(240, 24));
        navigation.add(posicionHistoricoLabel, BorderLayout.WEST);
        navigation.add(historialScrollBar, BorderLayout.CENTER);
        return navigation;
    }

    private void rebuildChartsPanel() {
        chartsPanel.removeAll();
        List<UsageChart> visibleCharts = new ArrayList<>();
        if (isChartEnabledForUi(StatsChartOption.RAM)) {
            visibleCharts.add(ramChart);
        }
        if (isChartEnabledForUi(StatsChartOption.DISK)) {
            visibleCharts.add(diskChart);
        }
        if (isChartEnabledForUi(StatsChartOption.PLAYERS)) {
            visibleCharts.add(playersChart);
        }
        for (int i = 0; i < visibleCharts.size(); i++) {
            UsageChart chart = visibleCharts.get(i);
            chart.setAlignmentX(Component.LEFT_ALIGNMENT);
            chartsPanel.add(chart);
            if (i < visibleCharts.size() - 1) {
                chartsPanel.add(Box.createVerticalStrut(12));
            }
        }
        updateChartHeights();
        chartsPanel.revalidate();
        chartsPanel.repaint();
    }

    private void updateChartHeights() {
        List<UsageChart> visibleCharts = new ArrayList<>();
        if (isChartEnabledForUi(StatsChartOption.RAM)) visibleCharts.add(ramChart);
        if (isChartEnabledForUi(StatsChartOption.DISK)) visibleCharts.add(diskChart);
        if (isChartEnabledForUi(StatsChartOption.PLAYERS)) visibleCharts.add(playersChart);
        if (visibleCharts.isEmpty()) {
            return;
        }
        int spacing = 12;
        int viewportHeight = Math.max(0, chartsScrollPane.getViewport().getHeight());
        int availableHeight = viewportHeight > 0 ? viewportHeight : (visibleCharts.size() * 220);
        int calculatedHeight = (availableHeight - (Math.max(0, visibleCharts.size() - 1) * spacing)) / visibleCharts.size();
        int chartHeight = Math.max(180, Math.min(260, calculatedHeight));
        for (UsageChart chart : visibleCharts) {
            chart.setPreferredChartHeight(chartHeight);
        }
    }

    private boolean isChartEnabledForUi(StatsChartOption option) {
        if (pendingChartEnabled.containsKey(option)) {
            return Boolean.TRUE.equals(pendingChartEnabled.get(option));
        }
        return isChartEnabled(option);
    }

    private JComponent crearSeparadorVertical(int height) {
        JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
        separator.setForeground(AppTheme.getSubtleBorderColor());
        separator.setPreferredSize(new Dimension(1, height));
        separator.setMaximumSize(new Dimension(1, height));
        return separator;
    }

    private void instalarNavegacionConRueda(Component component) {
        if (component == null) {
            return;
        }
        component.addMouseWheelListener(this::manejarRuedaHistorico);
    }

    private void manejarRuedaHistorico(MouseWheelEvent event) {
        if (!historialScrollBar.isEnabled()) {
            return;
        }
        int step = event.isShiftDown() ? historialScrollBar.getBlockIncrement() : historialScrollBar.getUnitIncrement();
        int delta = event.getWheelRotation() * Math.max(1, step);
        int nextValue = Math.max(historialScrollBar.getMinimum(),
                Math.min(historialScrollBar.getMaximum() - historialScrollBar.getVisibleAmount(), historialScrollBar.getValue() + delta));
        if (nextValue != historialScrollBar.getValue()) {
            historialScrollBar.setValue(nextValue);
        }
        event.consume();
    }

    private JPanel crearResumen() {
        JPanel summary = new JPanel(new GridLayout(1, 3, 8, 0));
        summary.setOpaque(false);
        summary.add(crearTarjetaResumen("RAM actual", ramActualValueLabel));
        summary.add(crearTarjetaResumen("Disco activo", discoActivoValueLabel));
        summary.add(crearTarjetaResumen("Estado", estadoValueLabel));
        return summary;
    }

    private JPanel crearTarjetaResumen(String title, JLabel valueLabel) {
        CardPanel card = new CardPanel(new BorderLayout(), new Insets(10, 10, 10, 10));
        card.setBackground(AppTheme.getSurfaceBackground());

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(AppTheme.getMutedForeground());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));

        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 16f));
        valueLabel.setForeground(AppTheme.getForeground());

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JPanel crearPanelAjustesHistorico() {
        CardPanel card = new CardPanel(new BorderLayout(), new Insets(10, 10, 10, 10));
        card.setBackground(AppTheme.getSurfaceBackground());

        JLabel titulo = new JLabel("Configuracion");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 13f));
        titulo.setForeground(AppTheme.getForeground());
        card.add(titulo, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        persistenciaCheckBox.setOpaque(false);
        persistenciaCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(persistenciaCheckBox);
        content.add(Box.createVerticalStrut(8));
        content.add(crearFilaSlider("Ventana reciente", ventanaRecienteSlider, ventanaRecienteValueLabel));
        content.add(Box.createVerticalStrut(8));
        content.add(crearFilaSlider("Resolucion historica", resolucionHistoricaSlider, resolucionHistoricaValueLabel));

        content.add(Box.createVerticalStrut(12));
        content.add(crearTablaConfiguracionGraficas());

        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private void abrirDialogoHistorico() {
        cargarAjustesHistoricoPersistidos();
        cargarControlesHistoricoDesdePersistidos();
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner instanceof Frame frame ? frame : null, "Configuracion", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(true);
        wrapper.setBackground(AppTheme.getBackground());
        wrapper.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        wrapper.add(crearPanelAjustesHistorico(), BorderLayout.CENTER);

        JButton cerrarButton = new JButton("Cerrar");
        styleActionButton(cerrarButton);
        cerrarButton.addActionListener(e -> dialog.dispose());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        footer.add(reiniciarHistoricoButton);
        footer.add(guardarHistoricoButton);
        footer.add(cerrarButton);
        wrapper.add(footer, BorderLayout.SOUTH);

        dialog.setContentPane(wrapper);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(520, dialog.getPreferredSize().height));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private JPanel crearFilaSlider(String titulo, JSlider slider, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel(titulo);
        titleLabel.setForeground(AppTheme.getMutedForeground());
        titleLabel.setPreferredSize(new Dimension(140, 24));

        valueLabel.setForeground(AppTheme.getForeground());
        valueLabel.setPreferredSize(new Dimension(90, 24));

        slider.setOpaque(false);

        row.add(titleLabel, BorderLayout.WEST);
        row.add(slider, BorderLayout.CENTER);
        row.add(valueLabel, BorderLayout.EAST);
        return row;
    }

    private JPanel crearFilaCombo(String titulo, JComponent component) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel(titulo);
        titleLabel.setForeground(AppTheme.getMutedForeground());
        titleLabel.setPreferredSize(new Dimension(140, 24));

        row.add(titleLabel, BorderLayout.WEST);
        row.add(component, BorderLayout.CENTER);
        return row;
    }


    private JPanel crearTablaConfiguracionGraficas() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel("Graficas");
        titleLabel.setForeground(AppTheme.getMutedForeground());
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(6));

        JPanel table = new JPanel(new GridBagLayout());
        table.setOpaque(false);
        table.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 6, 10);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        table.add(new JLabel("Metrica"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        table.add(new JLabel("Activa"), gbc);

        gbc.gridx = 2;
        table.add(new JLabel("Persistencia"), gbc);

        addGraficaConfigRow(table, 1, StatsChartOption.RAM, ramActivaCheckBox, ramPersistenciaCheckBox);
        addGraficaConfigRow(table, 2, StatsChartOption.DISK, discoActivaCheckBox, discoPersistenciaCheckBox);
        addGraficaConfigRow(table, 3, StatsChartOption.PLAYERS, jugadoresActivaCheckBox, jugadoresPersistenciaCheckBox);

        panel.add(table);
        return panel;
    }

    private void addGraficaConfigRow(JPanel table,
                                     int rowIndex,
                                     StatsChartOption option,
                                     JCheckBox activaCheckBox,
                                     JCheckBox persistenciaRowCheckBox) {
        activaCheckBox.setOpaque(false);
        persistenciaRowCheckBox.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = rowIndex;
        gbc.insets = new Insets(2, 0, 2, 10);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel label = new JLabel(option.toString());
        label.setForeground(AppTheme.getForeground());
        table.add(label, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        table.add(activaCheckBox, gbc);

        gbc.gridx = 2;
        table.add(persistenciaRowCheckBox, gbc);
    }


    private void configurarControlesHistorico() {
        ventanaRecienteSlider.setMinimum(0);
        ventanaRecienteSlider.setMaximum(RECENT_WINDOW_OPTIONS.length - 1);
        ventanaRecienteSlider.setPaintTicks(true);
        ventanaRecienteSlider.setMajorTickSpacing(1);
        ventanaRecienteSlider.setSnapToTicks(true);

        resolucionHistoricaSlider.setMinimum(0);
        resolucionHistoricaSlider.setMaximum(HISTORICAL_RESOLUTION_OPTIONS.length - 1);
        resolucionHistoricaSlider.setPaintTicks(true);
        resolucionHistoricaSlider.setMajorTickSpacing(1);
        resolucionHistoricaSlider.setSnapToTicks(true);

        persistenciaCheckBox.addActionListener(e -> {
            if (updatingSettingsControls) return;
            updateHistoricoSaveButtonState();
        });

        ventanaRecienteSlider.addChangeListener(e -> {
            int value = RECENT_WINDOW_OPTIONS[ventanaRecienteSlider.getValue()];
            ventanaRecienteValueLabel.setText(formatRecentWindowValue(value));
            if (updatingSettingsControls) return;
            updateHistoricoSaveButtonState();
        });

        resolucionHistoricaSlider.addChangeListener(e -> {
            int value = HISTORICAL_RESOLUTION_OPTIONS[resolucionHistoricaSlider.getValue()];
            resolucionHistoricaValueLabel.setText(formatHistoricalResolutionValue(value));
            if (updatingSettingsControls) return;
            updateHistoricoSaveButtonState();
        });

        ramActivaCheckBox.addActionListener(e -> actualizarGraficaVisiblePendiente(StatsChartOption.RAM, ramActivaCheckBox.isSelected()));
        discoActivaCheckBox.addActionListener(e -> actualizarGraficaVisiblePendiente(StatsChartOption.DISK, discoActivaCheckBox.isSelected()));
        jugadoresActivaCheckBox.addActionListener(e -> actualizarGraficaVisiblePendiente(StatsChartOption.PLAYERS, jugadoresActivaCheckBox.isSelected()));
        ramPersistenciaCheckBox.addActionListener(e -> actualizarPersistenciaGraficaPendiente(StatsChartOption.RAM, ramPersistenciaCheckBox.isSelected()));
        discoPersistenciaCheckBox.addActionListener(e -> actualizarPersistenciaGraficaPendiente(StatsChartOption.DISK, discoPersistenciaCheckBox.isSelected()));
        jugadoresPersistenciaCheckBox.addActionListener(e -> actualizarPersistenciaGraficaPendiente(StatsChartOption.PLAYERS, jugadoresPersistenciaCheckBox.isSelected()));

        reiniciarHistoricoButton.addActionListener(e -> cargarControlesHistoricoDesdePersistidos());
        guardarHistoricoButton.addActionListener(e -> guardarAjustesHistorico());

        cargarAjustesHistoricoPersistidos();
        cargarControlesHistoricoDesdePersistidos();
    }

    private void solicitarMuestras() {
        if (gestorServidores != null) {
            REGISTERED_GESTORES.add(gestorServidores);
        }
        int ramMaxMb = resolveMaxRamMb();
        int maxPlayers = resolveMaxPlayers();
        ramChart.setMaxValue(ramMaxMb);
        diskChart.setMaxValue(100);
        playersChart.setMaxValue(maxPlayers);

        if (server == null) {
            refrescarGraficasDesdeHistorial();
            ramActualValueLabel.setText("-");
            discoActivoValueLabel.setText("-");
            estadoValueLabel.setText("Sin servidor");
            return;
        }

        Process proceso = server.getServerProcess();
        if (proceso == null || !proceso.isAlive()) {
            refrescarGraficasDesdeHistorial();
            ramActualValueLabel.setText(buildRamText(statsHistory.lastRamValue(), ramMaxMb, false));
            discoActivoValueLabel.setText(buildDiskText((int) Math.round(Math.max(0d, Math.min(100d, statsHistory.lastDiskPercent()))), statsHistory.lastDiskSample()));
            estadoValueLabel.setText("Apagado");
            return;
        }
        refrescarGraficasDesdeHistorial();
        ramActualValueLabel.setText(buildRamText(statsHistory.lastRamValue(), ramMaxMb, true));
        discoActivoValueLabel.setText(buildDiskText((int) Math.round(Math.max(0d, Math.min(100d, statsHistory.lastDiskPercent()))), statsHistory.lastDiskSample()));
        estadoValueLabel.setText("En ejecución");
    }

    private long normalizeUsage(long usageMb, long maxMb) {
        long normalizedMb = Math.max(0L, usageMb);
        if (maxMb > 0L) {
            normalizedMb = Math.min(normalizedMb, maxMb);
        }
        return normalizedMb;
    }

    private String buildRamText(long usageMb, long maxMb, boolean mostrarPorcentaje) {
        if (maxMb <= 0L) {
            return formatMb(usageMb);
        }
        if (!mostrarPorcentaje) {
            return formatMb(usageMb) + " / " + formatMb(maxMb);
        }
        double percentage = (usageMb * 100.0d) / maxMb;
        return formatMb(usageMb) + " / " + formatMb(maxMb) + " (" + Math.round(percentage) + "%)";
    }

    private String buildDiskText(int diskPercent, DiskActivitySample diskSample) {
        return diskPercent + "%  L " + formatRate(diskSample.readBytesPerSec()) + "  E " + formatRate(diskSample.writeBytesPerSec());
    }

    private int resolveMaxRamMb() {
        if (server == null || server.getServerConfig() == null) {
            return 1;
        }
        return Math.max(1, server.getServerConfig().getRamMax());
    }

    private int resolveMaxPlayers() {
        try {
            if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
                return 20;
            }
            Path propertiesPath = Path.of(server.getServerDir()).resolve("server.properties");
            if (!Files.exists(propertiesPath)) {
                return 20;
            }
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(propertiesPath.toFile())) {
                props.load(fis);
            }
            String value = props.getProperty("max-players");
            if (value == null || value.isBlank()) {
                return 20;
            }
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (IOException | NumberFormatException ignored) {
            return 20;
        }
    }

    private boolean esMismoServidor(Server other) {
        if (server == null || other == null) {
            return false;
        }
        return server.getId() != null && server.getId().equals(other.getId());
    }

    private void detenerRecursos() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        if (gestorServidores != null && estadoListenerRegistrado) {
            gestorServidores.removePropertyChangeListener("estadoServidor", estadoServidorListener);
            estadoListenerRegistrado = false;
        }
        refreshTimer = null;
    }

    private void iniciarRecursosSiHaceFalta() {
        if (refreshTimer == null) {
            refreshTimer = new Timer(SAMPLE_INTERVAL_MS, e -> solicitarMuestras());
            refreshTimer.setInitialDelay(0);
        }
        if (!refreshTimer.isRunning()) {
            refreshTimer.start();
        }
        if (gestorServidores != null && !estadoListenerRegistrado) {
            gestorServidores.addPropertyChangeListener("estadoServidor", estadoServidorListener);
            estadoListenerRegistrado = true;
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        ToolTipManager.sharedInstance().setInitialDelay(0);
        ToolTipManager.sharedInstance().setReshowDelay(0);
        ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);
        iniciarRecursosSiHaceFalta();
        SwingUtilities.invokeLater(this::solicitarMuestras);
    }

    @Override
    public void removeNotify() {
        detenerRecursos();
        ToolTipManager.sharedInstance().setInitialDelay(originalTooltipInitialDelay);
        ToolTipManager.sharedInstance().setReshowDelay(originalTooltipReshowDelay);
        ToolTipManager.sharedInstance().setDismissDelay(originalTooltipDismissDelay);
        super.removeNotify();
    }

    private static long readServerRamUsageMb(Server server) {
        if (server == null) {
            return 0L;
        }
        Process process = server.getServerProcess();
        if (process == null || !process.isAlive()) {
            return 0L;
        }

        long pid = process.pid();
        long heapUsageMb = readHeapUsageWithJstat(pid);
        if (heapUsageMb >= 0L) {
            return heapUsageMb;
        }

        long residentUsageMb = readResidentUsageByOs(pid);
        return Math.max(0L, residentUsageMb);
    }

    private static DiskActivitySample readServerDiskActivity(Server server) {
        if (server == null) {
            return new DiskActivitySample(0d, 0L, 0L);
        }
        Process process = server.getServerProcess();
        if (process == null || !process.isAlive()) {
            return new DiskActivitySample(0d, 0L, 0L);
        }

        long pid = process.pid();
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return readWindowsDiskActivity(pid);
        }
        return new DiskActivitySample(0d, 0L, 0L);
    }

    private static DiskActivitySample readWindowsDiskActivity(long pid) {
        String script =
                "$targetPid = " + pid + "; " +
                "$proc = Get-CimInstance Win32_PerfFormattedData_PerfProc_Process | " +
                "Where-Object { $_.IDProcess -eq $targetPid } | Select-Object -First 1; " +
                "$disk = Get-CimInstance Win32_PerfFormattedData_PerfDisk_PhysicalDisk | " +
                "Where-Object { $_.Name -eq '_Total' } | Select-Object -First 1; " +
                "if ($proc -and $disk) { " +
                "[Console]::Out.Write(($proc.IOReadBytesPersec) + ';' + ($proc.IOWriteBytesPersec) + ';' + " +
                "($disk.DiskReadBytesPersec) + ';' + ($disk.DiskWriteBytesPersec) + ';' + " +
                "($disk.PercentDiskReadTime) + ';' + ($disk.PercentDiskWriteTime) + ';' + ($disk.PercentDiskTime)) }";

        String output = ejecutarComando(List.of("powershell", "-NoProfile", "-Command", script), 900);
        if (output == null || output.isBlank()) {
            return new DiskActivitySample(0d, 0L, 0L);
        }

        String[] parts = output.trim().split(";");
        if (parts.length < 7) {
            return new DiskActivitySample(0d, 0L, 0L);
        }

        long processReadBytes = parseLong(parts[0]);
        long processWriteBytes = parseLong(parts[1]);
        long totalReadBytes = Math.max(0L, parseLong(parts[2]));
        long totalWriteBytes = Math.max(0L, parseLong(parts[3]));
        double totalReadPercent = parseDouble(parts[4]);
        double totalWritePercent = parseDouble(parts[5]);
        double totalPercent = parseDouble(parts[6]);

        double percent = estimateProcessDiskPercent(
                processReadBytes,
                processWriteBytes,
                totalReadBytes,
                totalWriteBytes,
                totalReadPercent,
                totalWritePercent,
                totalPercent
        );

        return new DiskActivitySample(percent, Math.max(0L, processReadBytes), Math.max(0L, processWriteBytes));
    }

    private static double estimateProcessDiskPercent(long processReadBytes,
                                                     long processWriteBytes,
                                                     long totalReadBytes,
                                                     long totalWriteBytes,
                                                     double totalReadPercent,
                                                     double totalWritePercent,
                                                     double totalPercent) {
        double readContribution = 0d;
        double writeContribution = 0d;

        if (totalReadBytes > 0L && totalReadPercent > 0d) {
            readContribution = totalReadPercent * (processReadBytes / (double) totalReadBytes);
        }
        if (totalWriteBytes > 0L && totalWritePercent > 0d) {
            writeContribution = totalWritePercent * (processWriteBytes / (double) totalWriteBytes);
        }

        double estimatedPercent = readContribution + writeContribution;
        if (estimatedPercent <= 0d && totalPercent > 0d) {
            long processTotalBytes = Math.max(0L, processReadBytes) + Math.max(0L, processWriteBytes);
            long diskTotalBytes = Math.max(0L, totalReadBytes) + Math.max(0L, totalWriteBytes);
            if (diskTotalBytes > 0L) {
                estimatedPercent = totalPercent * (processTotalBytes / (double) diskTotalBytes);
            }
        }

        return Math.max(0d, Math.min(100d, estimatedPercent));
    }

    private static long readHeapUsageWithJstat(long pid) {
        String output = ejecutarComando(List.of("jstat", "-gc", String.valueOf(pid)), 700);
        if (output == null || output.isBlank()) {
            return -1L;
        }

        String[] lines = output.strip().split("\\R");
        if (lines.length < 2) {
            return -1L;
        }

        String headerLine = lines[0].trim();
        String valueLine = lines[lines.length - 1].trim();
        if (headerLine.isBlank() || valueLine.isBlank()) {
            return -1L;
        }

        String[] headers = headerLine.split("\\s+");
        String[] values = valueLine.split("\\s+");
        if (headers.length != values.length) {
            return -1L;
        }

        double usedKb = 0d;
        usedKb += readDoubleColumn(headers, values, "S0U");
        usedKb += readDoubleColumn(headers, values, "S1U");
        usedKb += readDoubleColumn(headers, values, "EU");
        usedKb += readDoubleColumn(headers, values, "OU");

        if (usedKb <= 0d) {
            return 0L;
        }
        return Math.round(usedKb / 1024d);
    }

    private static double readDoubleColumn(String[] headers, String[] values, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (!name.equalsIgnoreCase(headers[i])) {
                continue;
            }
            try {
                return Double.parseDouble(values[i]);
            } catch (NumberFormatException ignored) {
                return 0d;
            }
        }
        return 0d;
    }

    private static long readResidentUsageByOs(long pid) {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return readResidentUsageWindows(pid);
        }
        return readResidentUsageUnix(pid);
    }

    private static long readResidentUsageWindows(long pid) {
        String command = "$targetPid = " + pid + "; $p = Get-Process -Id $targetPid -ErrorAction SilentlyContinue; "
                + "if ($p) { [Console]::Out.Write($p.WorkingSet64) }";
        String output = ejecutarComando(List.of("powershell", "-NoProfile", "-Command", command), 700);
        if (output == null || output.isBlank()) {
            return -1L;
        }
        try {
            long bytes = Long.parseLong(output.trim());
            return Math.round(bytes / 1024d / 1024d);
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private static long readResidentUsageUnix(long pid) {
        String output = ejecutarComando(List.of("ps", "-o", "rss=", "-p", String.valueOf(pid)), 700);
        if (output == null || output.isBlank()) {
            return -1L;
        }
        try {
            long kb = Long.parseLong(output.trim());
            return Math.round(kb / 1024d);
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private static String ejecutarComando(List<String> command, long timeoutMs) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                process.getInputStream().transferTo(output);
                return output.toString(StandardCharsets.UTF_8).trim();
            }
        } catch (IOException | InterruptedException ignored) {
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
            }
            return null;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception ignored) {
            return 0d;
        }
    }

    private String formatMb(long mb) {
        return mb + " MB";
    }

    private String formatRate(long bytesPerSecond) {
        double value = Math.max(0L, bytesPerSecond);
        String[] units = {"B/s", "KB/s", "MB/s", "GB/s"};
        int unitIndex = 0;
        while (value >= 1024d && unitIndex < units.length - 1) {
            value /= 1024d;
            unitIndex++;
        }
        return String.format(Locale.US, unitIndex == 0 ? "%.0f %s" : "%.1f %s", value, units[unitIndex]);
    }

    private int indexForValue(int[] options, int value) {
        for (int i = 0; i < options.length; i++) {
            if (options[i] == value) {
                return i;
            }
        }
        int nearestIndex = 0;
        int nearestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < options.length; i++) {
            int distance = Math.abs(options[i] - value);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestIndex = i;
            }
        }
        return nearestIndex;
    }

    private String formatRecentWindowValue(int seconds) {
        int months = Math.max(1, seconds / (30 * 24 * 60 * 60));
        return months + (months == 1 ? " mes" : " meses");
    }

    private String formatHistoricalResolutionValue(int seconds) {
        if (seconds < 3600) {
            return (seconds / 60) + " min";
        }
        int hours = seconds / 3600;
        return hours + " h";
    }

    private String formatRecentWindow(int seconds) {
        int hours = seconds / 3600;
        if (hours >= 24 && hours % 24 == 0) {
            int days = hours / 24;
            return days + (days == 1 ? " día" : " días");
        }
        return hours + " h";
    }

    private String formatHistoricalResolution(int seconds) {
        int minutes = seconds / 60;
        return minutes + " min";
    }

    private int getStatsRangeSeconds() {
        Server settingsServer = getStatsSettingsServer();
        if (settingsServer == null || settingsServer.getEstadisticasRangoSegundos() == null || settingsServer.getEstadisticasRangoSegundos() <= 0) {
            return 300;
        }
        return settingsServer.getEstadisticasRangoSegundos();
    }

    private boolean isStatsPersistenceEnabled() {
        Server settingsServer = getStatsSettingsServer();
        return settingsServer == null || settingsServer.getEstadisticasPersistenciaActiva() == null
                ? true
                : Boolean.TRUE.equals(settingsServer.getEstadisticasPersistenciaActiva());
    }

    private int getStatsRecentWindowSeconds() {
        return getStatsRecentWindowSeconds(server);
    }

    private static int getStatsRecentWindowSeconds(Server server) {
        if (server == null || server.getEstadisticasVentanaRecienteSegundos() == null || server.getEstadisticasVentanaRecienteSegundos() <= 0) {
            return 30 * 24 * 60 * 60;
        }
        return server.getEstadisticasVentanaRecienteSegundos();
    }

    private int getStatsHistoricalResolutionSeconds() {
        return getStatsHistoricalResolutionSeconds(server);
    }

    private static int getStatsHistoricalResolutionSeconds(Server server) {
        if (server == null || server.getEstadisticasResolucionHistoricaSegundos() == null || server.getEstadisticasResolucionHistoricaSegundos() <= 0) {
            return 60;
        }
        return server.getEstadisticasResolucionHistoricaSegundos();
    }

    private boolean isChartEnabled(StatsChartOption option) {
        return isChartEnabled(getStatsSettingsServer(), option);
    }

    private boolean isChartPersistenceEnabled(StatsChartOption option) {
        return isChartPersistenceEnabled(getStatsSettingsServer(), option);
    }

    private static boolean isChartPersistenceEnabled(Server server, StatsChartOption option) {
        if (option == null) {
            return false;
        }
        if (server == null) {
            return true;
        }
        return switch (option) {
            case RAM -> server.getEstadisticasRamHistorial() == null || Boolean.TRUE.equals(server.getEstadisticasRamHistorial());
            case DISK -> server.getEstadisticasDiscoHistorial() == null || Boolean.TRUE.equals(server.getEstadisticasDiscoHistorial());
            case PLAYERS -> server.getEstadisticasJugadoresHistorial() == null || Boolean.TRUE.equals(server.getEstadisticasJugadoresHistorial());
        };
    }

    private static boolean isChartEnabled(Server server, StatsChartOption option) {
        if (option == null) {
            return false;
        }
        if (server == null) {
            return option.defaultEnabled();
        }
        return switch (option) {
            case RAM -> server.getEstadisticasRamActiva() == null ? option.defaultEnabled() : Boolean.TRUE.equals(server.getEstadisticasRamActiva());
            case DISK -> server.getEstadisticasDiscoActiva() == null ? option.defaultEnabled() : Boolean.TRUE.equals(server.getEstadisticasDiscoActiva());
            case PLAYERS -> server.getEstadisticasJugadoresActiva() == null ? option.defaultEnabled() : Boolean.TRUE.equals(server.getEstadisticasJugadoresActiva());
        };
    }

    private Server getStatsSettingsServer() {
        if (gestorServidores != null && server != null && server.getId() != null) {
            Server persistedServer = gestorServidores.getServerById(server.getId());
            if (persistedServer != null) {
                return persistedServer;
            }
        }
        return server;
    }

    private void guardarConfiguracionServidor() {
        if (gestorServidores != null && server != null) {
            gestorServidores.guardarServidor(server);
        }
    }

    private void cargarAjustesHistoricoPersistidos() {
        persistedStatsPersistenceEnabled = isStatsPersistenceEnabled();
        persistedStatsRecentWindowSeconds = getStatsRecentWindowSeconds();
        persistedStatsHistoricalResolutionSeconds = getStatsHistoricalResolutionSeconds();
        for (StatsChartOption option : StatsChartOption.values()) {
            boolean enabled = isChartEnabled(option);
            boolean persistenceEnabled = isChartPersistenceEnabled(option);
            persistedChartEnabled.put(option, enabled);
            pendingChartEnabled.put(option, enabled);
            persistedChartPersistenceEnabled.put(option, persistenceEnabled);
            pendingChartPersistenceEnabled.put(option, persistenceEnabled);
        }
    }

    private void cargarControlesHistoricoDesdePersistidos() {
        updatingSettingsControls = true;
        persistenciaCheckBox.setSelected(persistedStatsPersistenceEnabled);
        ventanaRecienteSlider.setValue(indexForValue(RECENT_WINDOW_OPTIONS, persistedStatsRecentWindowSeconds));
        ventanaRecienteValueLabel.setText(formatRecentWindowValue(RECENT_WINDOW_OPTIONS[ventanaRecienteSlider.getValue()]));
        resolucionHistoricaSlider.setValue(indexForValue(HISTORICAL_RESOLUTION_OPTIONS, persistedStatsHistoricalResolutionSeconds));
        resolucionHistoricaValueLabel.setText(formatHistoricalResolutionValue(HISTORICAL_RESOLUTION_OPTIONS[resolucionHistoricaSlider.getValue()]));
        ramActivaCheckBox.setSelected(Boolean.TRUE.equals(pendingChartEnabled.getOrDefault(StatsChartOption.RAM, StatsChartOption.RAM.defaultEnabled())));
        discoActivaCheckBox.setSelected(Boolean.TRUE.equals(pendingChartEnabled.getOrDefault(StatsChartOption.DISK, StatsChartOption.DISK.defaultEnabled())));
        jugadoresActivaCheckBox.setSelected(Boolean.TRUE.equals(pendingChartEnabled.getOrDefault(StatsChartOption.PLAYERS, StatsChartOption.PLAYERS.defaultEnabled())));
        ramPersistenciaCheckBox.setSelected(Boolean.TRUE.equals(pendingChartPersistenceEnabled.getOrDefault(StatsChartOption.RAM, true)));
        discoPersistenciaCheckBox.setSelected(Boolean.TRUE.equals(pendingChartPersistenceEnabled.getOrDefault(StatsChartOption.DISK, true)));
        jugadoresPersistenciaCheckBox.setSelected(Boolean.TRUE.equals(pendingChartPersistenceEnabled.getOrDefault(StatsChartOption.PLAYERS, true)));
        syncChartPersistenceCheckboxState(StatsChartOption.RAM);
        syncChartPersistenceCheckboxState(StatsChartOption.DISK);
        syncChartPersistenceCheckboxState(StatsChartOption.PLAYERS);
        updatingSettingsControls = false;
        updateHistoricoSaveButtonState();
        rebuildChartsPanel();
    }

    private boolean hasUnsavedHistoricoSettingsChanges() {
        if (server == null) {
            return false;
        }
        if (persistenciaCheckBox.isSelected() != persistedStatsPersistenceEnabled
                || RECENT_WINDOW_OPTIONS[ventanaRecienteSlider.getValue()] != persistedStatsRecentWindowSeconds
                || HISTORICAL_RESOLUTION_OPTIONS[resolucionHistoricaSlider.getValue()] != persistedStatsHistoricalResolutionSeconds) {
            return true;
        }
        for (StatsChartOption option : StatsChartOption.values()) {
            if (!Objects.equals(pendingChartEnabled.get(option), persistedChartEnabled.get(option))) {
                return true;
            }
            if (!Objects.equals(pendingChartPersistenceEnabled.get(option), persistedChartPersistenceEnabled.get(option))) {
                return true;
            }
        }
        return false;
    }

    private void updateHistoricoSaveButtonState() {
        boolean hasUnsavedChanges = !updatingSettingsControls && hasUnsavedHistoricoSettingsChanges();
        applyDefaultHistoricoSaveButtonStyle();
        guardarHistoricoButton.setEnabled(hasUnsavedChanges);
        reiniciarHistoricoButton.setEnabled(hasUnsavedChanges);
        if (hasUnsavedChanges) {
            guardarHistoricoButton.setBackground(AppTheme.getMainAccent());
            guardarHistoricoButton.setForeground(Color.WHITE);
            guardarHistoricoButton.setBorder(AppTheme.createAccentBorder(new Insets(6, 12, 6, 12), 1f));
        }
        guardarHistoricoButton.revalidate();
        guardarHistoricoButton.repaint();
        reiniciarHistoricoButton.revalidate();
        reiniciarHistoricoButton.repaint();
    }

    private void actualizarGraficaVisiblePendiente(StatsChartOption option, boolean enabled) {
        if (updatingSettingsControls) {
            return;
        }
        pendingChartEnabled.put(option, enabled);
        syncChartPersistenceCheckboxState(option);
        updateHistoricoSaveButtonState();
        rebuildChartsPanel();
    }

    private void actualizarPersistenciaGraficaPendiente(StatsChartOption option, boolean enabled) {
        if (updatingSettingsControls) {
            return;
        }
        pendingChartPersistenceEnabled.put(option, enabled);
        updateHistoricoSaveButtonState();
    }

    private void syncChartPersistenceCheckboxState(StatsChartOption option) {
        JCheckBox checkBox = getChartPersistenceCheckBox(option);
        if (checkBox == null) {
            return;
        }
        boolean chartEnabled = Boolean.TRUE.equals(pendingChartEnabled.getOrDefault(option, option.defaultEnabled()));
        checkBox.setEnabled(chartEnabled);
    }

    private JCheckBox getChartPersistenceCheckBox(StatsChartOption option) {
        return switch (option) {
            case RAM -> ramPersistenciaCheckBox;
            case DISK -> discoPersistenciaCheckBox;
            case PLAYERS -> jugadoresPersistenciaCheckBox;
        };
    }

    private void applyDefaultHistoricoSaveButtonStyle() {
        styleActionButton(guardarHistoricoButton);
        styleActionButton(reiniciarHistoricoButton);
    }

    private void guardarAjustesHistorico() {
        if (server == null || !hasUnsavedHistoricoSettingsChanges()) {
            updateHistoricoSaveButtonState();
            return;
        }
        boolean persistedPersistenceEnabled = persistedStatsPersistenceEnabled;
        boolean persistenciaActiva = persistenciaCheckBox.isSelected();
        int ventanaReciente = RECENT_WINDOW_OPTIONS[ventanaRecienteSlider.getValue()];
        int resolucionHistorica = HISTORICAL_RESOLUTION_OPTIONS[resolucionHistoricaSlider.getValue()];

        if (persistedPersistenceEnabled && !persistenciaActiva) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "La persistencia se va a desactivar. ¿Quieres borrar también el histórico guardado de este servidor?",
                    "Desactivar persistencia",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );
            if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                cargarControlesHistoricoDesdePersistidos();
                return;
            }
            limpiarHistorialActual();
            if (choice == JOptionPane.YES_OPTION) {
                borrarHistorialPersistido();
            }
        } else if (!persistedPersistenceEnabled && persistenciaActiva) {
            recargarHistorialPersistido();
        }

        server.setEstadisticasPersistenciaActiva(persistenciaActiva);
        server.setEstadisticasVentanaRecienteSegundos(ventanaReciente);
        server.setEstadisticasResolucionHistoricaSegundos(resolucionHistorica);
        aplicarConfiguracionGraficasEnServidor(server);

        Server persistedServer = getStatsSettingsServer();
        if (persistedServer != null && persistedServer != server) {
            persistedServer.setEstadisticasPersistenciaActiva(persistenciaActiva);
            persistedServer.setEstadisticasVentanaRecienteSegundos(ventanaReciente);
            persistedServer.setEstadisticasResolucionHistoricaSegundos(resolucionHistorica);
            aplicarConfiguracionGraficasEnServidor(persistedServer);
        }

        guardarConfiguracionServidor();
        cargarAjustesHistoricoPersistidos();
        refrescarGraficasDesdeHistorial();
        updateHistoricoSaveButtonState();
    }

    private void refrescarGraficasDesdeHistorial() {
        TimeRangeOption selectedRange = getSelectedRange();
        int bucketSeconds = getStatsHistoricalResolutionSeconds();
        actualizarNavegacionHistorico(selectedRange.seconds(), bucketSeconds);
        int offsetSeconds = getTimelineOffsetSeconds();
        ramChart.setSamples(statsHistory.snapshotForRange(selectedRange.seconds(), offsetSeconds, bucketSeconds));
        diskChart.setSamples(statsHistory.snapshotForRangeDisk(selectedRange.seconds(), offsetSeconds, bucketSeconds));
        playersChart.setSamples(statsHistory.snapshotForRangePlayers(selectedRange.seconds(), offsetSeconds, bucketSeconds));
        ramChart.setRangeSeconds(selectedRange.seconds());
        diskChart.setRangeSeconds(selectedRange.seconds());
        playersChart.setRangeSeconds(selectedRange.seconds());
        rebuildChartsPanel();
    }

    private TimeRangeOption getSelectedRange() {
        Object selected = rangoCombo.getSelectedItem();
        return selected instanceof TimeRangeOption option ? option : TimeRangeOption.SECONDS_60;
    }

    private int getTimelineOffsetSeconds() {
        return Math.max(0, timelineMaxOffsetSeconds - historialScrollBar.getValue());
    }

    private void actualizarNavegacionHistorico(int rangeSeconds, int bucketSeconds) {
        int maxOffsetSeconds = statsHistory.getMaxOffsetSeconds(rangeSeconds, bucketSeconds);
        int currentOffset = Math.max(0, Math.min(getTimelineOffsetSeconds(), maxOffsetSeconds));
        timelineMaxOffsetSeconds = maxOffsetSeconds;
        updatingTimelineControls = true;
        historialScrollBar.setEnabled(maxOffsetSeconds > 0);
        historialScrollBar.setValues(maxOffsetSeconds - currentOffset, 1, 0, maxOffsetSeconds + 1);
        historialScrollBar.setBlockIncrement(Math.max(10, rangeSeconds / 12));
        historialScrollBar.setUnitIncrement(1);
        updatingTimelineControls = false;
        posicionHistoricoLabel.setText(buildTimelineWindowLabel(currentOffset, bucketSeconds));
    }

    private String buildTimelineWindowLabel(int offsetSeconds, int bucketSeconds) {
        long endTimestamp = statsHistory.getWindowEndTimestamp(offsetSeconds, bucketSeconds);
        if (endTimestamp <= 0L) {
            return "Sin histórico";
        }
        String endText = SAMPLE_TIME_FORMAT.format(Instant.ofEpochMilli(endTimestamp));
        if (offsetSeconds <= 0) {
            return "Ventana actual hasta " + endText;
        }
        return "Ventana desplazada hasta " + endText;
    }

    private void styleActionButton(JButton button) {
        if (button == null) {
            return;
        }
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setOpaque(false);
        button.setContentAreaFilled(true);
        button.setBorderPainted(true);
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.setBorder(AppTheme.createRoundedBorder(new Insets(6, 12, 6, 12), 1f));
        button.setBackground(AppTheme.getSurfaceBackground());
        button.setForeground(AppTheme.getForeground());
    }

    private String getServerHistoryKey() {
        return (server != null && server.getId() != null && !server.getId().isBlank()) ? server.getId() : "__no_server__";
    }

    private void limpiarHistorialActual() {
        statsHistory.clear();
        refrescarGraficasDesdeHistorial();
        solicitarMuestras();
    }

    private void recargarHistorialPersistido() {
        StatsHistory loadedHistory = loadHistoryFromDisk(getStatsSettingsServer(), getServerHistoryKey());
        statsHistory.replaceWith(loadedHistory);
        refrescarGraficasDesdeHistorial();
        solicitarMuestras();
    }

    private void borrarHistorialPersistido() {
        try {
            Files.deleteIfExists(getHistoryFile(getStatsSettingsServer(), getServerHistoryKey()));
            Files.deleteIfExists(getLegacyHistoryFile(getServerHistoryKey()));
        } catch (IOException e) {
            System.err.println("No se ha podido borrar el historial de estadÃ­sticas de " + getServerHistoryKey() + ": " + e.getMessage());
        }
    }

    private void aplicarConfiguracionGraficasEnServidor(Server targetServer) {
        if (targetServer == null) {
            return;
        }
        targetServer.setEstadisticasRamActiva(Boolean.TRUE.equals(pendingChartEnabled.getOrDefault(StatsChartOption.RAM, StatsChartOption.RAM.defaultEnabled())));
        targetServer.setEstadisticasRamHistorial(Boolean.TRUE.equals(pendingChartPersistenceEnabled.getOrDefault(StatsChartOption.RAM, true)));
        targetServer.setEstadisticasDiscoActiva(Boolean.TRUE.equals(pendingChartEnabled.getOrDefault(StatsChartOption.DISK, StatsChartOption.DISK.defaultEnabled())));
        targetServer.setEstadisticasDiscoHistorial(Boolean.TRUE.equals(pendingChartPersistenceEnabled.getOrDefault(StatsChartOption.DISK, true)));
        targetServer.setEstadisticasJugadoresActiva(Boolean.TRUE.equals(pendingChartEnabled.getOrDefault(StatsChartOption.PLAYERS, StatsChartOption.PLAYERS.defaultEnabled())));
        targetServer.setEstadisticasJugadoresHistorial(Boolean.TRUE.equals(pendingChartPersistenceEnabled.getOrDefault(StatsChartOption.PLAYERS, true)));
    }

    private void abrirDialogoExportacion() {
        JOptionPane.showMessageDialog(this, "La nueva configuración avanzada de exportación se está preparando en la siguiente iteración.");
    }

    private JPanel crearFilaExportacion(String titulo, JComponent component) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        JLabel label = new JLabel(titulo);
        label.setForeground(AppTheme.getMutedForeground());
        label.setPreferredSize(new Dimension(140, 28));
        row.add(label, BorderLayout.WEST);
        row.add(component, BorderLayout.CENTER);
        return row;
    }

    private JPanel crearFilaChecksExportacion(String titulo, JComponent... components) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        JLabel label = new JLabel(titulo);
        label.setForeground(AppTheme.getMutedForeground());
        label.setPreferredSize(new Dimension(140, 28));
        JPanel right = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        right.setOpaque(false);
        for (JComponent component : components) {
            if (component instanceof JCheckBox checkBox) {
                checkBox.setOpaque(false);
            }
            right.add(component);
        }
        row.add(label, BorderLayout.WEST);
        row.add(right, BorderLayout.CENTER);
        return row;
    }

    private void configurarColorButton(JButton button, Color color) {
        button.setBackground(color);
        button.setForeground(contrastingTextColor(color));
    }

    private void seleccionarColor(Component parent, JButton button, String title, java.util.function.Consumer<Color> consumer) {
        Color selectedColor = JColorChooser.showDialog(parent, title, button.getBackground());
        if (selectedColor == null) {
            return;
        }
        consumer.accept(selectedColor);
        configurarColorButton(button, selectedColor);
    }

    private Color contrastingTextColor(Color background) {
        int luminance = (int) Math.round((background.getRed() * 0.299) + (background.getGreen() * 0.587) + (background.getBlue() * 0.114));
        return luminance >= 160 ? Color.BLACK : Color.WHITE;
    }

    private void actualizarPrevisualizacionExportacion(JLabel previewLabel,
                                                       TimeRangeOption tramo,
                                                       boolean incluirRam,
                                                       boolean incluirDisco,
                                                       ExportLayoutOption disposicion,
                                                       ExportPalette palette) {
        if (previewLabel == null) {
            return;
        }
        if (!incluirRam && !incluirDisco) {
            previewLabel.setIcon(null);
            previewLabel.setText("Selecciona al menos una gráfica");
            previewLabel.setForeground(AppTheme.getMutedForeground());
            return;
        }
        BufferedImage image = crearImagenExportacion(
                Math.max(720, previewLabel.getWidth() > 0 ? previewLabel.getWidth() : 760),
                320,
                tramo != null ? tramo : getSelectedRange(),
                incluirRam,
                incluirDisco,
                disposicion != null ? disposicion : ExportLayoutOption.SEPARADAS,
                palette
        );
        Image scaled = image.getScaledInstance(Math.max(720, previewLabel.getWidth() > 0 ? previewLabel.getWidth() : 760), 320, Image.SCALE_SMOOTH);
        previewLabel.setText(null);
        previewLabel.setIcon(new ImageIcon(scaled));
    }

    private void exportarGraficasComoPng(Component parent,
                                         TimeRangeOption tramo,
                                         boolean incluirRam,
                                         boolean incluirDisco,
                                         ExportLayoutOption disposicion,
                                         ExportPalette palette) throws IOException {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Exportar gráficas como PNG");
        chooser.setFileFilter(new FileNameExtensionFilter("Imagen PNG", "png"));
        java.io.File imagenesDir = FileSystemView.getFileSystemView().getDefaultDirectory();
        if (imagenesDir != null && imagenesDir.isDirectory()) {
            chooser.setCurrentDirectory(imagenesDir);
        }
        chooser.setSelectedFile(new java.io.File(getSuggestedExportName("png")));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path destino = chooser.getSelectedFile().toPath();
        if (!destino.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
            destino = destino.resolveSibling(destino.getFileName() + ".png");
        }
        confirmarSobrescrituraSiHaceFalta(parent, destino);

        BufferedImage image = crearImagenExportacion(1400, 340, tramo, incluirRam, incluirDisco, disposicion, palette);
        ImageIO.write(image, "png", destino.toFile());
    }

    private void exportarGraficasComoCsv(Component parent,
                                         TimeRangeOption tramo,
                                         boolean incluirRam,
                                         boolean incluirDisco) throws IOException {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Exportar gráficas como CSV");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV", "csv"));
        java.io.File documentosDir = FileSystemView.getFileSystemView().getDefaultDirectory();
        if (documentosDir != null && documentosDir.isDirectory()) {
            chooser.setCurrentDirectory(documentosDir);
        }
        chooser.setSelectedFile(new java.io.File(getSuggestedExportName("csv")));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path destino = chooser.getSelectedFile().toPath();
        if (!destino.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            destino = destino.resolveSibling(destino.getFileName() + ".csv");
        }
        confirmarSobrescrituraSiHaceFalta(parent, destino);

        ExportData exportData = buildExportData(tramo != null ? tramo : getSelectedRange());

        try (BufferedWriter writer = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            writer.write("metric,timestamp_iso,timestamp_millis,value,shutdown_marker");
            writer.newLine();
            if (incluirRam) {
                escribirMuestrasCsv(writer, "ram", exportData.ramSamples());
            }
            if (incluirDisco) {
                escribirMuestrasCsv(writer, "disk", exportData.diskSamples());
            }
        }
    }

    private BufferedImage crearImagenExportacion(int width,
                                                 int singleChartHeight,
                                                 TimeRangeOption tramo,
                                                 boolean incluirRam,
                                                 boolean incluirDisco,
                                                 ExportLayoutOption disposicion,
                                                 ExportPalette palette) {
        ExportData exportData = buildExportData(tramo != null ? tramo : getSelectedRange());
        List<BufferedImage> images = new ArrayList<>();
        if (disposicion == ExportLayoutOption.SUPERPUESTAS && incluirRam && incluirDisco) {
            images.add(renderOverlayExportChart(width, singleChartHeight, exportData, palette));
        } else {
            if (incluirRam) {
                images.add(ramChart.renderToImage(width, singleChartHeight, exportData.ramSamples(), exportData.rangeSeconds(), palette, palette.ramAccentColor()));
            }
            if (incluirDisco) {
                images.add(diskChart.renderToImage(width, singleChartHeight, exportData.diskSamples(), exportData.rangeSeconds(), palette, palette.diskAccentColor()));
            }
        }

        int spacing = images.size() > 1 ? 14 : 0;
        int finalWidth = images.stream().mapToInt(BufferedImage::getWidth).max().orElse(width);
        int finalHeight = images.stream().mapToInt(BufferedImage::getHeight).sum() + (Math.max(0, images.size() - 1) * spacing);
        BufferedImage combined = new BufferedImage(finalWidth, Math.max(1, finalHeight), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = combined.createGraphics();
        try {
            g2.setColor(palette.backgroundColor());
            g2.fillRect(0, 0, finalWidth, finalHeight);
            int y = 0;
            for (int i = 0; i < images.size(); i++) {
                BufferedImage image = images.get(i);
                g2.drawImage(image, 0, y, null);
                y += image.getHeight();
                if (i < images.size() - 1) {
                    y += spacing;
                }
            }
        } finally {
            g2.dispose();
        }
        return combined;
    }

    private ExportData buildExportData(TimeRangeOption tramo) {
        TimeRangeOption selected = tramo != null ? tramo : getSelectedRange();
        int bucketSeconds = getStatsHistoricalResolutionSeconds();
        int offsetSeconds = getTimelineOffsetSeconds();
        List<ChartSample> ramSamples = statsHistory.snapshotForRange(selected.seconds(), offsetSeconds, bucketSeconds);
        List<ChartSample> diskSamples = statsHistory.snapshotForRangeDisk(selected.seconds(), offsetSeconds, bucketSeconds);
        int ramMax = Math.max(1, resolveMaxRamMb());
        return new ExportData(ramSamples, diskSamples, selected.seconds(), ramMax, 100);
    }

    private BufferedImage renderOverlayExportChart(int width, int height, ExportData exportData, ExportPalette palette) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = AppTheme.getArc();
            g2.setColor(palette.backgroundColor());
            g2.fillRoundRect(0, 0, width, height, arc, arc);
            g2.setColor(palette.borderColor());
            g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);

            int chartWidth = Math.max(1, width - CHART_LEFT_PADDING - CHART_RIGHT_PADDING);
            int chartHeight = Math.max(1, height - CHART_TOP_PADDING - CHART_BOTTOM_PADDING);
            int x0 = CHART_LEFT_PADDING;
            int y0 = CHART_TOP_PADDING;
            int yBottom = y0 + chartHeight;

            g2.setColor(palette.textColor());
            g2.drawString("RAM + Disco", x0, 16);

            g2.setColor(palette.gridColor());
            for (int i = 0; i <= 4; i++) {
                int y = y0 + Math.round((chartHeight * i) / 4f);
                g2.drawLine(x0, y, x0 + chartWidth, y);
            }
            int stepSeconds = calcularPasoGridSegundosExport(exportData.rangeSeconds());
            for (int seconds = 0; seconds <= exportData.rangeSeconds(); seconds += stepSeconds) {
                float fraction = seconds / (float) exportData.rangeSeconds();
                int x = Math.round(x0 + (fraction * chartWidth));
                g2.drawLine(x, y0, x, yBottom);
                if (seconds < exportData.rangeSeconds()) {
                    String label = formatRangeLabelExport(exportData.rangeSeconds() - seconds);
                    FontMetrics fm = g2.getFontMetrics();
                    int labelX = Math.max(x0, Math.min(x0 + chartWidth - fm.stringWidth(label), x - (fm.stringWidth(label) / 2)));
                    g2.drawString(label, labelX, yBottom + 16);
                }
            }

            drawOverlaySeries(g2, exportData.ramSamples(), exportData.ramMaxValue(), x0, yBottom, chartWidth, chartHeight, palette.ramAccentColor());
            drawOverlaySeries(g2, exportData.diskSamples(), exportData.diskMaxValue(), x0, yBottom, chartWidth, chartHeight, palette.diskAccentColor());

            int legendY = 18;
            g2.setColor(palette.ramAccentColor());
            g2.fillRoundRect(width - 180, legendY - 8, 16, 8, 8, 8);
            g2.setColor(palette.textColor());
            g2.drawString("RAM", width - 158, legendY);
            g2.setColor(palette.diskAccentColor());
            g2.fillRoundRect(width - 100, legendY - 8, 16, 8, 8, 8);
            g2.setColor(palette.textColor());
            g2.drawString("Disco", width - 78, legendY);
        } finally {
            g2.dispose();
        }
        return image;
    }

    private void drawOverlaySeries(Graphics2D g2,
                                   List<ChartSample> samples,
                                   long maxValue,
                                   int x0,
                                   int yBottom,
                                   int chartWidth,
                                   int chartHeight,
                                   Color accent) {
        List<ChartSample> values = samples != null ? samples : List.of();
        Path2D.Float line = new Path2D.Float();
        boolean started = false;
        for (int i = 0; i < values.size(); i++) {
            ChartSample chartSample = values.get(i);
            if (chartSample == null) {
                continue;
            }
            long sample = Math.min(chartSample.value(), Math.max(1L, maxValue));
            float ratio = Math.max(0f, Math.min(1f, sample / (float) Math.max(1L, maxValue)));
            float x = x0 + (i / (float) Math.max(1, values.size() - 1)) * chartWidth;
            float y = yBottom - (ratio * chartHeight);
            if (!started) {
                started = true;
                line.moveTo(x, y);
            } else {
                line.lineTo(x, y);
            }
        }
        if (!started) {
            return;
        }
        g2.setColor(accent);
        g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(line);
        for (int i = 0; i < values.size(); i++) {
            ChartSample chartSample = values.get(i);
            if (chartSample == null || !chartSample.isShutdownMarker()) {
                continue;
            }
            long sample = Math.min(chartSample.value(), Math.max(1L, maxValue));
            float ratio = Math.max(0f, Math.min(1f, sample / (float) Math.max(1L, maxValue)));
            int shutdownX = Math.round(x0 + (i / (float) Math.max(1, values.size() - 1)) * chartWidth);
            int shutdownY = Math.round(yBottom - (ratio * chartHeight));
            g2.setColor(Color.BLACK);
            g2.fillOval(shutdownX - 3, shutdownY - 3, 6, 6);
        }
    }

    private void escribirMuestrasCsv(BufferedWriter writer, String metric, List<ChartSample> samples) throws IOException {
        for (ChartSample sample : samples) {
            if (sample == null) {
                continue;
            }
            writer.write(metric);
            writer.write(',');
            writer.write(Instant.ofEpochMilli(sample.timestampMillis()).toString());
            writer.write(',');
            writer.write(Long.toString(sample.timestampMillis()));
            writer.write(',');
            writer.write(Long.toString(sample.value()));
            writer.write(',');
            writer.write(Boolean.toString(sample.isShutdownMarker()));
            writer.newLine();
        }
    }

    private void confirmarSobrescrituraSiHaceFalta(Component parent, Path destino) throws IOException {
        if (!Files.exists(destino)) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(
                parent,
                "El archivo ya existe. ¿Quieres reemplazarlo?",
                "Exportar gráficas",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) {
            throw new IOException("EXPORT_CANCELLED");
        }
    }

    private String getSuggestedExportName(String extension) {
        String serverName = server != null && server.getDisplayName() != null && !server.getDisplayName().isBlank()
                ? server.getDisplayName().replaceAll("[^a-zA-Z0-9._-]", "_")
                : "estadisticas";
        return serverName + "-stats." + extension;
    }

    private int calcularPasoGridSegundosExport(int totalSeconds) {
        if (totalSeconds <= 60) return 10;
        if (totalSeconds <= 300) return 60;
        if (totalSeconds <= 600) return 120;
        if (totalSeconds <= 1800) return 300;
        return 600;
    }

    private String formatRangeLabelExport(int seconds) {
        if (seconds < 60) {
            return "-" + seconds + "s";
        }
        int minutes = seconds / 60;
        if (seconds % 60 == 0) {
            return "-" + minutes + "m";
        }
        return "-" + minutes + "m " + (seconds % 60) + "s";
    }

    private enum UsageMode {
        MEGABYTES,
        PERCENT,
        COUNT
    }

    private enum StatsChartOption {
        RAM("RAM", true),
        DISK("Disco", true),
        PLAYERS("Número de jugadores", false);

        private final String label;
        private final boolean defaultEnabled;

        StatsChartOption(String label, boolean defaultEnabled) {
            this.label = label;
            this.defaultEnabled = defaultEnabled;
        }

        private boolean defaultEnabled() {
            return defaultEnabled;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private record ExportPalette(Color backgroundColor,
                                 Color borderColor,
                                 Color gridColor,
                                 Color textColor,
                                 Color ramAccentColor,
                                 Color diskAccentColor) {
    }

    private record ExportData(List<ChartSample> ramSamples,
                              List<ChartSample> diskSamples,
                              int rangeSeconds,
                              long ramMaxValue,
                              long diskMaxValue) {
    }

    private static final class ExportPaletteModel {
        private Color backgroundColor;
        private Color borderColor;
        private Color gridColor;
        private Color textColor;
        private Color ramAccentColor;
        private Color diskAccentColor;

        private ExportPaletteModel(Color backgroundColor,
                                   Color borderColor,
                                   Color gridColor,
                                   Color textColor,
                                   Color ramAccentColor,
                                   Color diskAccentColor) {
            this.backgroundColor = backgroundColor;
            this.borderColor = borderColor;
            this.gridColor = gridColor;
            this.textColor = textColor;
            this.ramAccentColor = ramAccentColor;
            this.diskAccentColor = diskAccentColor;
        }

        private ExportPalette toPalette() {
            return new ExportPalette(backgroundColor, borderColor, gridColor, textColor, ramAccentColor, diskAccentColor);
        }
    }

    private enum ExportLayoutOption {
        SEPARADAS("Separadas"),
        SUPERPUESTAS("Superpuestas");

        private final String label;

        ExportLayoutOption(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum TimeRangeOption {
        SECONDS_60("60 s", 60),
        MINUTES_5("5 min", 300),
        MINUTES_10("10 min", 600),
        MINUTES_30("30 min", 1800),
        HOUR_1("1 h", 3600);

        private final String label;
        private final int seconds;

        TimeRangeOption(String label, int seconds) {
            this.label = label;
            this.seconds = seconds;
        }

        private int seconds() {
            return seconds;
        }

        private static TimeRangeOption fromSeconds(int seconds) {
            for (TimeRangeOption option : values()) {
                if (option.seconds == seconds) {
                    return option;
                }
            }
            return SECONDS_60;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private record DiskActivitySample(double percent, long readBytesPerSec, long writeBytesPerSec) {
    }

    private record ChartSample(long value, long timestampMillis, Boolean shutdownMarker) {
        private ChartSample(long value, long timestampMillis) {
            this(value, timestampMillis, false);
        }

        private ChartSample {
            shutdownMarker = Boolean.TRUE.equals(shutdownMarker);
        }

        private boolean isShutdownMarker() {
            return Boolean.TRUE.equals(shutdownMarker);
        }
    }

    private static final class AggregateSample {
        public long bucketStartMillis;
        public long sum;
        public long count;

        public AggregateSample() {
        }

        private AggregateSample(long bucketStartMillis) {
            this.bucketStartMillis = bucketStartMillis;
        }

        private void add(long value) {
            sum += value;
            count++;
        }
    }

    private static final class PersistedStatsHistory {
        public List<ChartSample> recentRam = new ArrayList<>();
        public List<ChartSample> recentDisk = new ArrayList<>();
        public List<ChartSample> recentPlayers = new ArrayList<>();
        public List<AggregateSample> archiveRam = new ArrayList<>();
        public List<AggregateSample> archiveDisk = new ArrayList<>();
        public List<AggregateSample> archivePlayers = new ArrayList<>();
        public long lastRamValue;
        public double lastDiskPercent;
        public long lastDiskReadBytesPerSec;
        public long lastDiskWriteBytesPerSec;
        public long lastPlayersValue;
        public boolean active;
    }

    private static final class StatsHistory {
        private final Deque<ChartSample> ramSamples = new ArrayDeque<>();
        private final Deque<ChartSample> diskSamples = new ArrayDeque<>();
        private final Deque<ChartSample> playerSamples = new ArrayDeque<>();
        private final TreeMap<Long, AggregateSample> ramArchive = new TreeMap<>();
        private final TreeMap<Long, AggregateSample> diskArchive = new TreeMap<>();
        private final TreeMap<Long, AggregateSample> playerArchive = new TreeMap<>();
        private volatile long lastRamValue = 0L;
        private volatile DiskActivitySample lastDiskSample = new DiskActivitySample(0d, 0L, 0L);
        private volatile long lastPlayersValue = 0L;
        private volatile boolean active = false;
        private volatile boolean dirty = false;
        private final LinkedHashSet<String> connectedPlayers = new LinkedHashSet<>();
        private int processedRawLogCount = 0;

        private synchronized void addRamSample(long value, long timestampMillis, int recentWindowSeconds, int bucketSeconds) {
            addSample(ramSamples, value, timestampMillis);
            lastRamValue = value;
            compactOldSamples(ramSamples, ramArchive, recentWindowSeconds, bucketSeconds);
            dirty = true;
        }

        private synchronized void addDiskSample(long value, long timestampMillis, int recentWindowSeconds, int bucketSeconds) {
            addSample(diskSamples, value, timestampMillis);
            compactOldSamples(diskSamples, diskArchive, recentWindowSeconds, bucketSeconds);
            dirty = true;
        }

        private synchronized void addPlayerSample(long value, long timestampMillis, int recentWindowSeconds, int bucketSeconds) {
            addSample(playerSamples, value, timestampMillis);
            lastPlayersValue = value;
            compactOldSamples(playerSamples, playerArchive, recentWindowSeconds, bucketSeconds);
            dirty = true;
        }

        private synchronized List<ChartSample> snapshotForRange(int seconds, int offsetSeconds, int bucketSeconds) {
            return buildSnapshot(ramSamples, ramArchive, seconds, offsetSeconds, bucketSeconds);
        }

        private synchronized List<ChartSample> snapshotForRangeDisk(int seconds, int offsetSeconds, int bucketSeconds) {
            return buildSnapshot(diskSamples, diskArchive, seconds, offsetSeconds, bucketSeconds);
        }

        private synchronized List<ChartSample> snapshotForRangePlayers(int seconds, int offsetSeconds, int bucketSeconds) {
            return buildSnapshot(playerSamples, playerArchive, seconds, offsetSeconds, bucketSeconds);
        }

        private void addSample(Deque<ChartSample> samples, long value, long timestampMillis) {
            addSample(samples, value, timestampMillis, false);
        }

        private void addSample(Deque<ChartSample> samples, long value, long timestampMillis, boolean shutdownMarker) {
            samples.addLast(new ChartSample(Math.max(0L, value), timestampMillis, shutdownMarker));
        }

        private synchronized void markActive() {
            active = true;
        }

        private synchronized void markInactive() {
            active = false;
        }

        private synchronized boolean isActive() {
            return active;
        }

        private synchronized void addShutdownSamples(long timestampMillis, int recentWindowSeconds, int bucketSeconds) {
            addSample(ramSamples, 0L, timestampMillis, true);
            addSample(diskSamples, 0L, timestampMillis, true);
            addSample(playerSamples, 0L, timestampMillis, true);
            compactOldSamples(ramSamples, ramArchive, recentWindowSeconds, bucketSeconds);
            compactOldSamples(diskSamples, diskArchive, recentWindowSeconds, bucketSeconds);
            compactOldSamples(playerSamples, playerArchive, recentWindowSeconds, bucketSeconds);
            lastRamValue = 0L;
            lastDiskSample = new DiskActivitySample(0d, 0L, 0L);
            lastPlayersValue = 0L;
            connectedPlayers.clear();
            processedRawLogCount = 0;
            active = false;
            dirty = true;
        }

        private long lastRamValue() {
            return lastRamValue;
        }

        private double lastDiskPercent() {
            return lastDiskSample.percent();
        }

        private DiskActivitySample lastDiskSample() {
            return lastDiskSample;
        }

        private long lastPlayersValue() {
            return lastPlayersValue;
        }

        private synchronized long syncAndGetCurrentPlayers(Server server) {
            if (server == null) {
                connectedPlayers.clear();
                processedRawLogCount = 0;
                lastPlayersValue = 0L;
                return 0L;
            }
            List<String> rawLogLines = server.getRawLogLines();
            if (rawLogLines == null) {
                return connectedPlayers.size();
            }
            if (rawLogLines.size() < processedRawLogCount) {
                connectedPlayers.clear();
                processedRawLogCount = 0;
            }
            for (int i = processedRawLogCount; i < rawLogLines.size(); i++) {
                String line = rawLogLines.get(i);
                if (line == null || line.isBlank()) {
                    continue;
                }
                Matcher joinMatcher = PLAYER_JOIN.matcher(line);
                if (joinMatcher.find()) {
                    connectedPlayers.add(joinMatcher.group(1));
                    continue;
                }
                Matcher leftMatcher = PLAYER_LEFT.matcher(line);
                if (leftMatcher.find()) {
                    connectedPlayers.remove(leftMatcher.group(1));
                }
            }
            processedRawLogCount = rawLogLines.size();
            lastPlayersValue = connectedPlayers.size();
            return lastPlayersValue;
        }

        private void setLastDiskSample(DiskActivitySample sample) {
            lastDiskSample = sample != null ? sample : new DiskActivitySample(0d, 0L, 0L);
            dirty = true;
        }

        private List<ChartSample> buildSnapshot(Deque<ChartSample> recentSamples,
                                                TreeMap<Long, AggregateSample> archiveSamples,
                                                int seconds,
                                                int offsetSeconds,
                                                int bucketSeconds) {
            int rangeSeconds = Math.max(1, seconds);
            List<ChartSample> timeline = buildTimelineSamples(recentSamples, archiveSamples, bucketSeconds);
            List<ChartSample> snapshot = new ArrayList<>(rangeSeconds);
            for (int i = 0; i < rangeSeconds; i++) {
                snapshot.add(null);
            }

            if (timeline.isEmpty()) {
                return snapshot;
            }

            int safeOffset = Math.max(0, offsetSeconds);
            int endIndexExclusive = Math.max(0, timeline.size() - safeOffset);
            int startIndex = Math.max(0, endIndexExclusive - rangeSeconds);
            int copyStart = Math.max(0, rangeSeconds - (endIndexExclusive - startIndex));
            for (int timelineIndex = startIndex; timelineIndex < endIndexExclusive; timelineIndex++) {
                int snapshotIndex = copyStart + (timelineIndex - startIndex);
                if (snapshotIndex >= 0 && snapshotIndex < snapshot.size()) {
                    snapshot.set(snapshotIndex, timeline.get(timelineIndex));
                }
            }
            return snapshot;
        }

        private List<ChartSample> buildTimelineSamples(Deque<ChartSample> recentSamples,
                                                       TreeMap<Long, AggregateSample> archiveSamples,
                                                       int bucketSeconds) {
            List<ChartSample> timeline = new ArrayList<>(archiveSamples.size() + recentSamples.size());
            for (AggregateSample aggregate : archiveSamples.values()) {
                if (aggregate == null || aggregate.count <= 0L) {
                    continue;
                }
                long averageValue = Math.round(aggregate.sum / (double) aggregate.count);
                timeline.add(new ChartSample(averageValue, aggregate.bucketStartMillis, false));
            }
            timeline.addAll(recentSamples);
            return timeline;
        }

        private void compactOldSamples(Deque<ChartSample> recentSamples,
                                       TreeMap<Long, AggregateSample> archiveSamples,
                                       int recentWindowSeconds,
                                       int bucketSeconds) {
            if (recentSamples.isEmpty() || recentWindowSeconds <= 0 || bucketSeconds <= 0) {
                return;
            }

            long cutoffTimestamp = System.currentTimeMillis() - (recentWindowSeconds * 1000L);
            while (!recentSamples.isEmpty()) {
                ChartSample sample = recentSamples.peekFirst();
                if (sample == null || sample.timestampMillis() >= cutoffTimestamp) {
                    break;
                }
                recentSamples.removeFirst();
                long bucketStart = (sample.timestampMillis() / (bucketSeconds * 1000L)) * (bucketSeconds * 1000L);
                AggregateSample aggregate = archiveSamples.computeIfAbsent(bucketStart, ignored -> new AggregateSample(bucketStart));
                aggregate.add(sample.value());
            }
        }

        private synchronized int getMaxOffsetSeconds(int rangeSeconds, int bucketSeconds) {
            int totalSamples = Math.max(buildTimelineSamples(ramSamples, ramArchive, bucketSeconds).size(),
                    Math.max(buildTimelineSamples(diskSamples, diskArchive, bucketSeconds).size(),
                            buildTimelineSamples(playerSamples, playerArchive, bucketSeconds).size()));
            return Math.max(0, totalSamples - Math.max(1, rangeSeconds));
        }

        private synchronized long getWindowEndTimestamp(int offsetSeconds, int bucketSeconds) {
            List<ChartSample> timeline = buildTimelineSamples(ramSamples, ramArchive, bucketSeconds);
            if (buildTimelineSamples(diskSamples, diskArchive, bucketSeconds).size() > timeline.size()) {
                timeline = buildTimelineSamples(diskSamples, diskArchive, bucketSeconds);
            }
            if (buildTimelineSamples(playerSamples, playerArchive, bucketSeconds).size() > timeline.size()) {
                timeline = buildTimelineSamples(playerSamples, playerArchive, bucketSeconds);
            }
            if (timeline.isEmpty()) {
                return 0L;
            }
            int safeOffset = Math.max(0, Math.min(offsetSeconds, timeline.size() - 1));
            return timeline.get(timeline.size() - 1 - safeOffset).timestampMillis();
        }

        private synchronized PersistedStatsHistory toPersistedModel() {
            PersistedStatsHistory model = new PersistedStatsHistory();
            model.recentRam = new ArrayList<>(ramSamples);
            model.recentDisk = new ArrayList<>(diskSamples);
            model.recentPlayers = new ArrayList<>(playerSamples);
            model.archiveRam = new ArrayList<>(ramArchive.values());
            model.archiveDisk = new ArrayList<>(diskArchive.values());
            model.archivePlayers = new ArrayList<>(playerArchive.values());
            model.lastRamValue = lastRamValue;
            model.lastDiskPercent = lastDiskSample.percent();
            model.lastDiskReadBytesPerSec = lastDiskSample.readBytesPerSec();
            model.lastDiskWriteBytesPerSec = lastDiskSample.writeBytesPerSec();
            model.lastPlayersValue = lastPlayersValue;
            model.active = active;
            return model;
        }

        private static StatsHistory fromPersistedModel(PersistedStatsHistory model) {
            StatsHistory history = new StatsHistory();
            if (model == null) {
                return history;
            }
            if (model.recentRam != null) {
                history.ramSamples.addAll(model.recentRam);
            }
            if (model.recentDisk != null) {
                history.diskSamples.addAll(model.recentDisk);
            }
            if (model.recentPlayers != null) {
                history.playerSamples.addAll(model.recentPlayers);
            }
            if (model.archiveRam != null) {
                for (AggregateSample sample : model.archiveRam) {
                    if (sample != null) {
                        history.ramArchive.put(sample.bucketStartMillis, sample);
                    }
                }
            }
            if (model.archiveDisk != null) {
                for (AggregateSample sample : model.archiveDisk) {
                    if (sample != null) {
                        history.diskArchive.put(sample.bucketStartMillis, sample);
                    }
                }
            }
            if (model.archivePlayers != null) {
                for (AggregateSample sample : model.archivePlayers) {
                    if (sample != null) {
                        history.playerArchive.put(sample.bucketStartMillis, sample);
                    }
                }
            }
            history.lastRamValue = model.lastRamValue;
            history.lastDiskSample = new DiskActivitySample(
                    model.lastDiskPercent,
                    model.lastDiskReadBytesPerSec,
                    model.lastDiskWriteBytesPerSec
            );
            history.lastPlayersValue = model.lastPlayersValue;
            history.active = model.active;
            history.dirty = false;
            return history;
        }

        private synchronized void clear() {
            ramSamples.clear();
            diskSamples.clear();
            playerSamples.clear();
            ramArchive.clear();
            diskArchive.clear();
            playerArchive.clear();
            lastRamValue = 0L;
            lastDiskSample = new DiskActivitySample(0d, 0L, 0L);
            lastPlayersValue = 0L;
            connectedPlayers.clear();
            processedRawLogCount = 0;
            active = false;
            dirty = false;
        }

        private synchronized void clearMetricHistory(StatsChartOption option) {
            if (option == null) {
                return;
            }
            switch (option) {
                case RAM -> {
                    ramSamples.clear();
                    ramArchive.clear();
                    lastRamValue = 0L;
                }
                case DISK -> {
                    diskSamples.clear();
                    diskArchive.clear();
                    lastDiskSample = new DiskActivitySample(0d, 0L, 0L);
                }
                case PLAYERS -> {
                    playerSamples.clear();
                    playerArchive.clear();
                    lastPlayersValue = 0L;
                    connectedPlayers.clear();
                    processedRawLogCount = 0;
                }
            }
            dirty = true;
        }

        private synchronized void replaceWith(StatsHistory other) {
            clear();
            if (other == null) {
                return;
            }
            ramSamples.addAll(other.ramSamples);
            diskSamples.addAll(other.diskSamples);
            playerSamples.addAll(other.playerSamples);
            ramArchive.putAll(other.ramArchive);
            diskArchive.putAll(other.diskArchive);
            playerArchive.putAll(other.playerArchive);
            lastRamValue = other.lastRamValue;
            lastDiskSample = other.lastDiskSample;
            lastPlayersValue = other.lastPlayersValue;
            active = other.active;
            dirty = other.dirty;
        }
    }

    private static StatsHistory getOrCreateHistory(Server server) {
        String key = (server != null && server.getId() != null && !server.getId().isBlank())
                ? server.getId()
                : "__no_server__";
        StatsHistory history = HISTORY_BY_SERVER.get(key);
        if (history != null) {
            return history;
        }
        StatsHistory loadedHistory = loadHistoryFromDisk(server, key);
        HISTORY_BY_SERVER.put(key, loadedHistory);
        return loadedHistory;
    }

    private static StatsHistory loadHistoryFromDisk(Server server, String serverKey) {
        if (server != null && !Boolean.TRUE.equals(server.getEstadisticasPersistenciaActiva())) {
            return new StatsHistory();
        }
        return loadHistoryFromDiskByKey(server, serverKey);
    }

    private static StatsHistory loadHistoryFromDiskByKey(String serverKey) {
        return loadHistoryFromDiskByKey(findServerByKey(serverKey), serverKey);
    }

    private static StatsHistory loadHistoryFromDiskByKey(Server server, String serverKey) {
        Path historyFile = getHistoryFile(server, serverKey);
        migrateLegacyHistoryIfNeeded(server, serverKey, historyFile);
        if (!Files.exists(historyFile)) {
            return new StatsHistory();
        }
        try {
            PersistedStatsHistory model = HISTORY_MAPPER.readValue(historyFile.toFile(), PersistedStatsHistory.class);
            return StatsHistory.fromPersistedModel(model);
        } catch (Exception e) {
            System.err.println("No se ha podido cargar el historial de estadísticas de " + serverKey + ": " + e.getMessage());
            return new StatsHistory();
        }
    }

    private static void flushDirtyHistories() {
        for (Map.Entry<String, StatsHistory> entry : HISTORY_BY_SERVER.entrySet()) {
            String serverKey = entry.getKey();
            StatsHistory history = entry.getValue();
            if (serverKey == null || history == null || !history.dirty) {
                continue;
            }
            Server server = findServerByKey(serverKey);
            if (server != null && !Boolean.TRUE.equals(server.getEstadisticasPersistenciaActiva())) {
                continue;
            }
            saveHistoryToDisk(serverKey, history);
        }
    }

    private static void saveHistoryToDisk(String serverKey, StatsHistory history) {
        try {
            Server persistedServer = findServerByKey(serverKey);
            Path historyFile = getHistoryFile(persistedServer, serverKey);
            Files.createDirectories(historyFile.getParent());
            PersistedStatsHistory model = history.toPersistedModel();
            HISTORY_MAPPER.writerWithDefaultPrettyPrinter().writeValue(historyFile.toFile(), model);
            cleanupLegacyHistoryFile(serverKey, historyFile);
            history.dirty = false;
        } catch (Exception e) {
            System.err.println("No se ha podido guardar el historial de estadísticas de " + serverKey + ": " + e.getMessage());
        }
    }

    private static Path getHistoryFile(Server server, String serverKey) {
        if (server != null && server.getServerDir() != null && !server.getServerDir().isBlank()) {
            return Path.of(server.getServerDir()).resolve("easy-mc-stats.json");
        }
        return getLegacyHistoryFile(serverKey);
    }

    private static Path getLegacyHistoryFile(String serverKey) {
        String safeName = (serverKey == null || serverKey.isBlank()) ? "__no_server__" : serverKey.replaceAll("[^a-zA-Z0-9._-]", "_");
        return GestorConfiguracion.getBaseDirectory().resolve("stats").resolve(safeName + ".json");
    }

    private static void migrateLegacyHistoryIfNeeded(Server server, String serverKey, Path targetHistoryFile) {
        Path legacyHistoryFile = getLegacyHistoryFile(serverKey);
        if (Objects.equals(legacyHistoryFile, targetHistoryFile) || !Files.exists(legacyHistoryFile) || Files.exists(targetHistoryFile)) {
            return;
        }
        try {
            Files.createDirectories(targetHistoryFile.getParent());
            Files.move(legacyHistoryFile, targetHistoryFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("No se ha podido migrar el historial de estadÃ­sticas de " + serverKey + ": " + e.getMessage());
        }
    }

    private static void cleanupLegacyHistoryFile(String serverKey, Path currentHistoryFile) {
        Path legacyHistoryFile = getLegacyHistoryFile(serverKey);
        if (Objects.equals(legacyHistoryFile, currentHistoryFile)) {
            return;
        }
        try {
            Files.deleteIfExists(legacyHistoryFile);
        } catch (IOException ignored) {
        }
    }

    private static void sampleActiveServers() {
        for (GestorServidores gestor : REGISTERED_GESTORES) {
            if (gestor == null || gestor.getListaServidores() == null) {
                continue;
            }
            for (Server currentServer : gestor.getListaServidores()) {
                if (currentServer == null) {
                    continue;
                }
                StatsHistory history = getOrCreateHistory(currentServer);
                Process process = currentServer.getServerProcess();
                int recentWindowSeconds = getStatsRecentWindowSeconds(currentServer);
                int bucketSeconds = getStatsHistoricalResolutionSeconds(currentServer);
                if (process == null || !process.isAlive()) {
                    if (history.isActive()) {
                        history.addShutdownSamples(System.currentTimeMillis(), recentWindowSeconds, bucketSeconds);
                    } else {
                        history.markInactive();
                    }
                    continue;
                }

                history.markActive();
                int ramMaxMb = Math.max(1, currentServer.getServerConfig() != null ? currentServer.getServerConfig().getRamMax() : 1);
                long ramUsage = normalizeStaticUsage(readServerRamUsageMb(currentServer), ramMaxMb);
                DiskActivitySample diskSample = readServerDiskActivity(currentServer);
                int diskPercent = (int) Math.round(Math.max(0d, Math.min(100d, diskSample.percent())));
                long playersCount = history.syncAndGetCurrentPlayers(currentServer);
                long timestamp = System.currentTimeMillis();

                history.addRamSample(ramUsage, timestamp, recentWindowSeconds, bucketSeconds);
                history.addDiskSample(diskPercent, timestamp, recentWindowSeconds, bucketSeconds);
                history.addPlayerSample(playersCount, timestamp, recentWindowSeconds, bucketSeconds);
                history.setLastDiskSample(diskSample);
            }
        }
    }

    private static Server findServerByKey(String serverKey) {
        for (GestorServidores gestor : REGISTERED_GESTORES) {
            if (gestor == null || gestor.getListaServidores() == null) {
                continue;
            }
            for (Server currentServer : gestor.getListaServidores()) {
                if (currentServer == null || currentServer.getId() == null) {
                    continue;
                }
                if (currentServer.getId().equals(serverKey)) {
                    return currentServer;
                }
            }
        }
        return null;
    }

    private static long normalizeStaticUsage(long usageMb, long maxMb) {
        long normalizedMb = Math.max(0L, usageMb);
        if (maxMb > 0L) {
            normalizedMb = Math.min(normalizedMb, maxMb);
        }
        return normalizedMb;
    }

    private final class UsageChart extends JComponent {
        private final String title;
        private final UsageMode mode;
        private final String emptyMessage;
        private long maxValue = 1L;
        private int rangeSeconds = 60;
        private int hoveredSampleIndex = -1;
        private Long hoveredTimestampMillis = null;
        private String lastTooltipText;
        private List<ChartSample> samples = new ArrayList<>();

        private UsageChart(String title, UsageMode mode, String emptyMessage) {
            this.title = title;
            this.mode = mode;
            this.emptyMessage = emptyMessage;
            setOpaque(false);
            setPreferredSize(new Dimension(0, 260));
            setMinimumSize(new Dimension(0, 220));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
            ToolTipManager.sharedInstance().registerComponent(this);
            MouseAdapter hoverListener = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    updateTooltipAt(e.getX(), e.getY());
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoveredSampleIndex = -1;
                    hoveredTimestampMillis = null;
                    lastTooltipText = null;
                    setToolTipText(null);
                    repaint();
                }
            };
            addMouseMotionListener(hoverListener);
            addMouseListener(hoverListener);
        }

        private void setMaxValue(long maxValue) {
            this.maxValue = Math.max(1L, maxValue);
            repaint();
        }

        private void setRangeSeconds(int rangeSeconds) {
            this.rangeSeconds = Math.max(1, rangeSeconds);
            repaint();
        }

        private void setPreferredChartHeight(int preferredHeight) {
            int normalizedHeight = Math.max(160, preferredHeight);
            setPreferredSize(new Dimension(0, normalizedHeight));
            setMinimumSize(new Dimension(0, 160));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
            revalidate();
            repaint();
        }

        private void setSamples(List<ChartSample> samples) {
            this.samples = samples != null ? new ArrayList<>(samples) : new ArrayList<>();
            if (hoveredTimestampMillis != null) {
                hoveredSampleIndex = findSampleIndexByTimestamp(hoveredTimestampMillis);
                if (hoveredSampleIndex < 0) {
                    hoveredTimestampMillis = null;
                    lastTooltipText = null;
                    setToolTipText(null);
                }
            }
            repaint();
        }

        private List<ChartSample> getSamplesSnapshot() {
            return new ArrayList<>(samples);
        }

        private BufferedImage renderToImage(int width,
                                            int height,
                                            List<ChartSample> renderSamples,
                                            int renderRangeSeconds,
                                            ExportPalette palette,
                                            Color accentColor) {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                drawChart(g2, width, height, renderSamples, renderRangeSeconds, palette, accentColor, false);
            } finally {
                g2.dispose();
            }
            return image;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                drawChart(g2,
                        getWidth(),
                        getHeight(),
                        samples,
                        rangeSeconds,
                        new ExportPalette(
                                AppTheme.getSurfaceBackground(),
                                AppTheme.getBorderColor(),
                                AppTheme.getSubtleBorderColor(),
                                AppTheme.getForeground(),
                                AppTheme.getMainAccent(),
                                AppTheme.getMainAccent()
                        ),
                        AppTheme.getMainAccent(),
                        true);
            } finally {
                g2.dispose();
            }
        }

        private void drawChart(Graphics2D g2,
                               int width,
                               int height,
                               List<ChartSample> renderSamples,
                               int renderRangeSeconds,
                               ExportPalette palette,
                               Color accent,
                               boolean includeHover) {
            if (width <= 0 || height <= 0) {
                return;
            }

            int arc = AppTheme.getArc();
            g2.setColor(palette.backgroundColor());
            g2.fillRoundRect(0, 0, width, height, arc, arc);
            g2.setColor(palette.borderColor());
            g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);

            int chartWidth = Math.max(1, width - CHART_LEFT_PADDING - CHART_RIGHT_PADDING);
            int chartHeight = Math.max(1, height - CHART_TOP_PADDING - CHART_BOTTOM_PADDING);
            int x0 = CHART_LEFT_PADDING;
            int y0 = CHART_TOP_PADDING;
            int yBottom = CHART_TOP_PADDING + chartHeight;

            g2.setColor(palette.textColor());
            g2.drawString(title, x0, 16);
            g2.drawString(formatAxisValue(maxValue), 12, CHART_TOP_PADDING + 5);
            g2.drawString(formatAxisValue(0), 16, yBottom);

            g2.setColor(palette.gridColor());
            for (int i = 0; i <= 4; i++) {
                int y = y0 + Math.round((chartHeight * i) / 4f);
                g2.drawLine(x0, y, x0 + chartWidth, y);
            }
            int safeRangeSeconds = Math.max(1, renderRangeSeconds);
            int stepSeconds = calcularPasoGridSegundos(safeRangeSeconds);
            for (int seconds = 0; seconds <= safeRangeSeconds; seconds += stepSeconds) {
                float fraction = seconds / (float) safeRangeSeconds;
                int x = Math.round(x0 + (fraction * chartWidth));
                g2.drawLine(x, y0, x, yBottom);
                if (seconds < safeRangeSeconds) {
                    String label = formatRangeLabel(safeRangeSeconds - seconds);
                    FontMetrics fm = g2.getFontMetrics();
                    int labelX = Math.max(x0, Math.min(x0 + chartWidth - fm.stringWidth(label), x - (fm.stringWidth(label) / 2)));
                    g2.drawString(label, labelX, yBottom + 16);
                }
            }

            List<ChartSample> values = renderSamples != null ? new ArrayList<>(renderSamples) : new ArrayList<>();
            boolean hasAnyValue = values.stream().anyMatch(Objects::nonNull);
            if (!hasAnyValue) {
                g2.setColor(palette.textColor());
                drawCenteredString(g2, emptyMessage, new Rectangle(x0, y0, chartWidth, chartHeight));
                return;
            }

            Path2D.Float area = new Path2D.Float();
            Path2D.Float line = new Path2D.Float();
            boolean started = false;
            int latestIndex = -1;
            long latest = 0L;

            for (int i = 0; i < values.size(); i++) {
                ChartSample chartSample = values.get(i);
                if (chartSample == null) {
                    continue;
                }
                long sample = Math.min(chartSample.value(), maxValue);
                float ratio = Math.max(0f, Math.min(1f, sample / (float) maxValue));
                float x = x0 + (i / (float) Math.max(1, values.size() - 1)) * chartWidth;
                float y = yBottom - (ratio * chartHeight);
                if (!started) {
                    started = true;
                    line.moveTo(x, y);
                    area.moveTo(x, yBottom);
                    area.lineTo(x, y);
                } else {
                    line.lineTo(x, y);
                    area.lineTo(x, y);
                }
                latestIndex = i;
                latest = sample;
            }

            if (!started) {
                g2.setColor(palette.textColor());
                drawCenteredString(g2, emptyMessage, new Rectangle(x0, y0, chartWidth, chartHeight));
                return;
            }

            area.lineTo(x0 + chartWidth, yBottom);
            area.closePath();

            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 52));
            g2.fill(area);

            g2.setColor(accent);
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(line);

            for (int i = 0; i < values.size(); i++) {
                ChartSample chartSample = values.get(i);
                if (chartSample == null || !chartSample.isShutdownMarker()) {
                    continue;
                }
                long sample = Math.min(chartSample.value(), maxValue);
                float ratio = Math.max(0f, Math.min(1f, sample / (float) maxValue));
                int shutdownX = Math.round(x0 + (i / (float) Math.max(1, values.size() - 1)) * chartWidth);
                int shutdownY = Math.round(yBottom - (ratio * chartHeight));
                g2.setColor(palette.backgroundColor());
                g2.fillOval(shutdownX - 5, shutdownY - 5, 10, 10);
                g2.setColor(Color.BLACK);
                g2.fillOval(shutdownX - 3, shutdownY - 3, 6, 6);
            }

            float latestRatio = Math.max(0f, Math.min(1f, latest / (float) maxValue));
            int latestX = Math.round(x0 + (latestIndex / (float) Math.max(1, values.size() - 1)) * chartWidth);
            int latestY = Math.round(yBottom - (latestRatio * chartHeight));

            g2.setColor(palette.backgroundColor());
            g2.fillOval(latestX - 5, latestY - 5, 10, 10);
            g2.setColor(values.get(latestIndex) != null && values.get(latestIndex).isShutdownMarker() ? Color.BLACK : accent);
            g2.fillOval(latestX - 3, latestY - 3, 6, 6);

            String latestValueLabel = formatAxisValue(latest);
            FontMetrics latestValueMetrics = g2.getFontMetrics();
            int latestLabelX = Math.min(width - latestValueMetrics.stringWidth(latestValueLabel) - 8, latestX + 12);
            int latestLabelY = Math.max(y0 + latestValueMetrics.getAscent(), Math.min(yBottom - 4, latestY + (latestValueMetrics.getAscent() / 2)));
            g2.setColor(palette.textColor());
            g2.drawString(latestValueLabel, latestLabelX, latestLabelY);

            if (includeHover && hoveredSampleIndex >= 0 && hoveredSampleIndex < values.size() && values.get(hoveredSampleIndex) != null) {
                ChartSample hoveredSample = values.get(hoveredSampleIndex);
                long hoveredValue = Math.min(hoveredSample.value(), maxValue);
                float hoveredRatio = Math.max(0f, Math.min(1f, hoveredValue / (float) maxValue));
                int hoveredX = Math.round(x0 + (hoveredSampleIndex / (float) Math.max(1, values.size() - 1)) * chartWidth);
                int hoveredY = Math.round(yBottom - (hoveredRatio * chartHeight));

                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 90));
                g2.drawLine(hoveredX, y0, hoveredX, yBottom);

                g2.setColor(palette.backgroundColor());
                g2.fillOval(hoveredX - 6, hoveredY - 6, 12, 12);
                g2.setColor(hoveredSample.isShutdownMarker() ? Color.BLACK : accent);
                g2.fillOval(hoveredX - 4, hoveredY - 4, 8, 8);
            }

            g2.setColor(palette.textColor());
            g2.drawString("Ahora", x0 + chartWidth - 28, height - 8);
        }

        private String formatAxisValue(long value) {
            if (mode == UsageMode.PERCENT) {
                return value + "%";
            }
            if (mode == UsageMode.COUNT) {
                return Long.toString(value);
            }
            return formatMb(value);
        }

        private void updateTooltipAt(int mouseX, int mouseY) {
            HoverInfo hoverInfo = findNearestSample(mouseX, mouseY);
            if (hoverInfo == null) {
                hoveredSampleIndex = -1;
                hoveredTimestampMillis = null;
                lastTooltipText = null;
                setToolTipText(null);
                repaint();
                return;
            }
            hoveredSampleIndex = hoverInfo.index();
            ChartSample sample = hoverInfo.sample();
            hoveredTimestampMillis = sample.timestampMillis();
            String valueText = mode == UsageMode.PERCENT ? (sample.value() + "%")
                    : mode == UsageMode.COUNT ? Long.toString(sample.value())
                    : formatMb(sample.value());
            String timeText = SAMPLE_TIME_FORMAT.format(Instant.ofEpochMilli(sample.timestampMillis()));
            String tooltipText = "<html><b>" + title + "</b>: " + valueText + "<br>" + timeText + "</html>";
            if (!Objects.equals(lastTooltipText, tooltipText)) {
                lastTooltipText = tooltipText;
                setToolTipText(tooltipText);
                ToolTipManager.sharedInstance().mouseMoved(new MouseEvent(
                        this,
                        MouseEvent.MOUSE_MOVED,
                        System.currentTimeMillis(),
                        0,
                        mouseX,
                        mouseY,
                        0,
                        false
                ));
            }
            repaint();
        }

        private HoverInfo findNearestSample(int mouseX, int mouseY) {
            List<ChartSample> values = new ArrayList<>(samples);
            if (values.isEmpty()) {
                return null;
            }

            int chartWidth = Math.max(1, getWidth() - CHART_LEFT_PADDING - CHART_RIGHT_PADDING);
            int chartHeight = Math.max(1, getHeight() - CHART_TOP_PADDING - CHART_BOTTOM_PADDING);
            int x0 = CHART_LEFT_PADDING;
            int y0 = CHART_TOP_PADDING;
            int yBottom = CHART_TOP_PADDING + chartHeight;
            if (mouseX < x0 || mouseX > x0 + chartWidth || mouseY < y0 || mouseY > yBottom) {
                return null;
            }

            double relativeX = (mouseX - x0) / (double) chartWidth;
            relativeX = Math.max(0d, Math.min(1d, relativeX));
            int nearestIndex = (int) Math.round(relativeX * Math.max(1, values.size() - 1));
            nearestIndex = Math.max(0, Math.min(values.size() - 1, nearestIndex));

            ChartSample sample = values.get(nearestIndex);
            if (sample == null) {
                int leftIndex = nearestIndex - 1;
                int rightIndex = nearestIndex + 1;
                while (leftIndex >= 0 || rightIndex < values.size()) {
                    if (leftIndex >= 0 && values.get(leftIndex) != null) {
                        return new HoverInfo(leftIndex, values.get(leftIndex));
                    }
                    if (rightIndex < values.size() && values.get(rightIndex) != null) {
                        return new HoverInfo(rightIndex, values.get(rightIndex));
                    }
                    leftIndex--;
                    rightIndex++;
                }
                return null;
            }
            return new HoverInfo(nearestIndex, sample);
        }

        private int findSampleIndexByTimestamp(long timestampMillis) {
            for (int i = 0; i < samples.size(); i++) {
                ChartSample sample = samples.get(i);
                if (sample != null && sample.timestampMillis() == timestampMillis) {
                    return i;
                }
            }
            return -1;
        }

        private void drawCenteredString(Graphics2D g2, String text, Rectangle area) {
            FontMetrics fm = g2.getFontMetrics();
            int x = area.x + (area.width - fm.stringWidth(text)) / 2;
            int y = area.y + ((area.height - fm.getHeight()) / 2) + fm.getAscent();
            g2.drawString(text, x, y);
        }

        private int calcularPasoGridSegundos(int totalSeconds) {
            if (totalSeconds <= 60) return 10;
            if (totalSeconds <= 300) return 60;
            if (totalSeconds <= 600) return 120;
            if (totalSeconds <= 1800) return 300;
            return 600;
        }

        private String formatRangeLabel(int seconds) {
            if (seconds < 60) {
                return "-" + seconds + "s";
            }
            int minutes = seconds / 60;
            if (seconds % 60 == 0) {
                return "-" + minutes + "m";
            }
            return "-" + minutes + "m " + (seconds % 60) + "s";
        }
    }

    private record HoverInfo(int index, ChartSample sample) {
    }
}
