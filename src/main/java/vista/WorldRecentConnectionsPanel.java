package vista;

import com.formdev.flatlaf.extras.components.FlatButton;
import com.formdev.flatlaf.extras.components.FlatScrollPane;
import controlador.world.WorldPlayerDataService;
import modelo.Server;
import modelo.World;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.beans.PropertyChangeListener;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WorldRecentConnectionsPanel {
    private static final Pattern JOIN = Pattern.compile("([^\\s]+) joined the game");
    private static final Pattern HORA_LOG = Pattern.compile("^\\[(\\d{2}:\\d{2}:\\d{2})]\\s*");
    private static final DateTimeFormatter FORMATO_HORA_LOG = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATO_CONEXION = DateTimeFormatter.ofPattern("dd/MM/yyyy - HH:mm:ss");

    private final Supplier<Server> serverSupplier;
    private final Supplier<World> worldSupplier;
    private final JPanel conexionesPanel = new JPanel(new BorderLayout());
    private final JPanel conexionesRowsPanel = new JPanel(new GridBagLayout());
    private final FlatScrollPane conexionesScrollPane = new FlatScrollPane();
    private final JButton btnDebugAddConnection = new FlatButton();
    private final JButton btnDebugRemoveConnection = new FlatButton();
    private final List<RecentConnection> conexionesDebug = new ArrayList<>();
    private final PropertyChangeListener debugModeListener;
    private JPanel headerActionsPanel;
    private int fakeConnectionSequence;

    WorldRecentConnectionsPanel(Supplier<Server> serverSupplier, Supplier<World> worldSupplier) {
        this.serverSupplier = serverSupplier;
        this.worldSupplier = worldSupplier;
        conexionesPanel.setOpaque(false);
        conexionesRowsPanel.setOpaque(false);
        conexionesScrollPane.setViewportView(conexionesRowsPanel);
        conexionesScrollPane.setBorder(BorderFactory.createEmptyBorder());
        conexionesScrollPane.getViewport().setOpaque(false);
        conexionesScrollPane.setOpaque(false);
        conexionesScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        conexionesScrollPane.getVerticalScrollBar().setUnitIncrement(18);
        conexionesScrollPane.getVerticalScrollBar().setBlockIncrement(72);
        conexionesPanel.add(conexionesScrollPane, BorderLayout.CENTER);

        AppTheme.configureDebugIconButton(btnDebugAddConnection, "Añadir conexión falsa", "easymcicons/plus.svg", this::addFakeConnection);
        AppTheme.configureDebugIconButton(btnDebugRemoveConnection, "Eliminar conexión falsa", "easymcicons/minus.svg", this::removeFakeConnection);
        debugModeListener = evt -> {
            if (!DebugMode.PROPERTY_ENABLED.equals(evt.getPropertyName())) return;
            SwingUtilities.invokeLater(this::actualizarModoDebugConexiones);
        };
    }

    JPanel crearTarjeta() {
        CardPanel card = new CardPanel("Últimas conexiones");
        headerActionsPanel = card.getHeaderActionsPanel();
        actualizarAccionesDebugConexiones();
        card.getContentPanel().add(conexionesPanel, BorderLayout.CENTER);
        card.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) == 0) return;
            if (card.isDisplayable()) {
                DebugMode.addPropertyChangeListener(debugModeListener);
            } else {
                DebugMode.removePropertyChangeListener(debugModeListener);
            }
        });
        return card;
    }

    void actualizar() {
        Server server = serverSupplier.get();
        List<RecentConnection> conexionesReales = server == null
                ? List.of()
                : obtenerUltimasConexiones(server, worldSupplier.get());
        renderConexiones(aplicarConexionesDebug(conexionesReales));
    }

    private void actualizarAccionesDebugConexiones() {
        if (headerActionsPanel == null) return;
        headerActionsPanel.removeAll();
        if (DebugMode.isEnabled()) {
            headerActionsPanel.add(btnDebugAddConnection);
            headerActionsPanel.add(btnDebugRemoveConnection);
        }
        headerActionsPanel.revalidate();
        headerActionsPanel.repaint();
    }

    private void actualizarModoDebugConexiones() {
        if (!DebugMode.isEnabled() && !conexionesDebug.isEmpty()) {
            conexionesDebug.clear();
            actualizar();
        }
        actualizarAccionesDebugConexiones();
    }

    private List<RecentConnection> aplicarConexionesDebug(List<RecentConnection> conexiones) {
        if (!DebugMode.isEnabled() || conexionesDebug.isEmpty()) {
            return deduplicateRecentConnections(conexiones);
        }

        return mergeDebugRecentConnections(conexionesDebug, conexiones);
    }

    static List<RecentConnection> mergeDebugRecentConnections(
            List<RecentConnection> debugConnections,
            List<RecentConnection> realConnections
    ) {
        ArrayList<RecentConnection> combined = new ArrayList<>();
        if (debugConnections != null) {
            combined.addAll(debugConnections);
        }
        if (realConnections != null) {
            combined.addAll(realConnections);
        }
        return deduplicateRecentConnections(combined);
    }

    static List<RecentConnection> deduplicateRecentConnections(List<RecentConnection> connections) {
        if (connections == null || connections.isEmpty()) {
            return List.of();
        }

        ArrayList<RecentConnection> deduplicated = new ArrayList<>();
        Set<String> usernamesVistos = new HashSet<>();
        for (RecentConnection connection : connections) {
            if (connection == null) continue;

            String username = normalizeUsername(connection.username());
            if (username == null || !usernamesVistos.add(username)) {
                continue;
            }

            deduplicated.add(connection);
        }
        return List.copyOf(deduplicated);
    }

    static List<RecentConnection> extractRecentConnectionsFromLogLines(
            List<String> rawLogLines,
            LocalDate fechaBase
    ) {
        if (rawLogLines == null || rawLogLines.isEmpty()) {
            return List.of();
        }

        ArrayList<RecentConnection> conexiones = new ArrayList<>();
        Set<String> usernamesVistos = new HashSet<>();
        LocalDate fecha = fechaBase == null ? LocalDate.now() : fechaBase;

        for (int i = rawLogLines.size() - 1; i >= 0; i--) {
            String raw = rawLogLines.get(i);
            if (raw == null || raw.isBlank()) continue;

            Matcher joinMatcher = JOIN.matcher(raw);
            if (!joinMatcher.find()) continue;

            String jugador = joinMatcher.group(1);
            String jugadorNormalizado = normalizeUsername(jugador);
            if (jugadorNormalizado == null || !usernamesVistos.add(jugadorNormalizado)) {
                continue;
            }

            String timestamp = fecha.format(FORMATO_FECHA) + " - --:--:--";
            Matcher horaMatcher = HORA_LOG.matcher(raw);
            if (horaMatcher.find()) {
                try {
                    LocalTime hora = LocalTime.parse(horaMatcher.group(1), FORMATO_HORA_LOG);
                    timestamp = FORMATO_CONEXION.format(fecha.atTime(hora));
                } catch (DateTimeParseException ignored) {
                }
            }

            conexiones.add(new RecentConnection(jugador, timestamp, null));
        }
        return List.copyOf(conexiones);
    }

    private void addFakeConnection() {
        fakeConnectionSequence++;
        String username = String.format(Locale.ROOT, "DebugPlayer%03d", fakeConnectionSequence);
        String timestamp = FORMATO_CONEXION.format(java.time.LocalDateTime.now());
        String location = "X: " + (fakeConnectionSequence * 37) + " Z: " + (fakeConnectionSequence * -19);
        conexionesDebug.add(0, new RecentConnection(username, timestamp, location, System.currentTimeMillis()));
        actualizar();
    }

    private void removeFakeConnection() {
        if (conexionesDebug.isEmpty()) return;

        conexionesDebug.remove(0);
        actualizar();
    }

    private List<RecentConnection> obtenerUltimasConexiones(Server server, World mundo) {
        List<RecentConnection> conexionesDesdeLogs = extractRecentConnectionsFromLogLines(
                server.getRawLogLines(),
                LocalDate.now()
        );
        if (!conexionesDesdeLogs.isEmpty()) {
            return conexionesDesdeLogs;
        }

        return obtenerUltimosJugadoresDesdePlayerdata(server, mundo);
    }

    private List<RecentConnection> obtenerUltimosJugadoresDesdePlayerdata(Server server, World mundo) {
        return deduplicateRecentConnections(
                WorldPlayerDataService.findRecentPlayers(server, mundo, 0).stream()
                        .map(player -> new RecentConnection(
                                player.username(),
                                FORMATO_CONEXION.format(player.lastSeen().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()),
                                "X: " + player.point().x() + " Z: " + player.point().z(),
                                player.lastSeen().toMillis()
                        ))
                        .toList()
        );
    }

    private static String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }
        String normalized = username.strip().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private void renderConexiones(List<RecentConnection> conexiones) {
        conexionesRowsPanel.removeAll();

        if (conexiones == null || conexiones.isEmpty()) {
            JLabel vacio = new JLabel("No hay conexiones recientes.");
            vacio.setForeground(AppTheme.getMutedForeground());
            vacio.setAlignmentX(Component.LEFT_ALIGNMENT);
            GridBagConstraints emptyConstraints = new GridBagConstraints();
            emptyConstraints.gridx = 0;
            emptyConstraints.gridy = 0;
            emptyConstraints.weightx = 1.0;
            emptyConstraints.fill = GridBagConstraints.HORIZONTAL;
            emptyConstraints.anchor = GridBagConstraints.NORTHWEST;
            conexionesRowsPanel.add(vacio, emptyConstraints);
        } else {
            GridBagConstraints rowConstraints = new GridBagConstraints();
            rowConstraints.gridx = 0;
            rowConstraints.weightx = 1.0;
            rowConstraints.fill = GridBagConstraints.HORIZONTAL;
            rowConstraints.anchor = GridBagConstraints.NORTHWEST;

            for (int i = 0; i < conexiones.size(); i++) {
                rowConstraints.gridy = i;
                rowConstraints.weighty = 0.0;
                conexionesRowsPanel.add(crearFilaConexion(conexiones.get(i)), rowConstraints);
            }
        }

        GridBagConstraints fillerConstraints = new GridBagConstraints();
        fillerConstraints.gridx = 0;
        fillerConstraints.gridy = conexiones == null ? 1 : conexiones.size();
        fillerConstraints.weightx = 1.0;
        fillerConstraints.weighty = 1.0;
        fillerConstraints.fill = GridBagConstraints.BOTH;
        JPanel filler = new JPanel();
        filler.setOpaque(false);
        conexionesRowsPanel.add(filler, fillerConstraints);

        conexionesRowsPanel.revalidate();
        conexionesRowsPanel.repaint();
        conexionesPanel.revalidate();
        conexionesPanel.repaint();
    }

    private JPanel crearFilaConexion(RecentConnection conexion) {
        JPanel fila = new JPanel(new BorderLayout(10, 0));
        fila.setOpaque(false);
        fila.setAlignmentX(Component.LEFT_ALIGNMENT);

        PlayerIdentityView identidad = new PlayerIdentityView(conexion.username(), PlayerIdentityView.SizePreset.COMPACT);
        identidad.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel fecha = new JLabel(conexion.timestamp());
        fecha.setForeground(AppTheme.getMutedForeground());
        fecha.setVerticalAlignment(SwingConstants.CENTER);

        int rowHeight = Math.max(identidad.getPreferredSize().height, fecha.getPreferredSize().height);
        fila.setPreferredSize(new Dimension(0, rowHeight));
        fila.setMaximumSize(new Dimension(Integer.MAX_VALUE, rowHeight));
        fila.setMinimumSize(new Dimension(0, rowHeight));

        fila.add(identidad, BorderLayout.CENTER);
        fila.add(fecha, BorderLayout.EAST);
        return fila;
    }

    record RecentConnection(String username, String timestamp, String location, long sortEpochMillis) {
        private RecentConnection(String username, String timestamp, String location) {
            this(username, timestamp, location, 0L);
        }
    }
}
