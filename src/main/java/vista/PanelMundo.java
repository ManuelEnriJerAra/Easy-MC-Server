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
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.ExecutionException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

public class PanelMundo extends JPanel {
    private final GestorServidores gestorServidores;

    private final JLabel mundoActivoLabel = new JLabel();
    private final JLabel tiempoRealLabel = new JLabel("Tiempo activo: -");
    private final JLabel lastPlayedLabel = new JLabel("Nunca");
    private final JLabel previewImageLabel = new JLabel("La preview todavia no existe.", SwingConstants.CENTER);
    // La seed se muestra en un campo de texto no editable para que el usuario pueda
    // seleccionarla y copiarla facilmente al compararla con visores externos.
    private final JTextField seedField = new JTextField("-");
    private final JComboBox<World> mundosCombo = new JComboBox<>();
    private final JButton refrescarButton = new JButton("Refrescar");
    private final JButton usarEsteMundoButton = new JButton("Usar este mundo");
    private final JButton importarButton = new JButton("Importar mundo");
    private final JButton exportarButton = new JButton("Exportar mundo");
    private final JButton generarButton = new JButton("Generar nuevo mundo");
    private final JButton generarPreviewButton = new JButton("Generar preview");
    private final JCheckBox senalarSpawnCheckBox = new JCheckBox("Señalar spawn", true);
    private final JCheckBox generarTodoCheckBox = new JCheckBox("Generar todo", false);

    private World mundoActivoActual;
    private final Runnable onWorldChanged;
    private String mundoTicksCache = "";
    private long ticksMostradosCache = 0L;
    private long ticksArchivoCache = 0L;
    private long ultimaActualizacionTicksMs = 0L;
    private boolean actualizandoComboMundos = false;

    PanelMundo(GestorServidores gestorServidores, Runnable onWorldChanged){
        this.gestorServidores = gestorServidores;
        this.onWorldChanged = onWorldChanged;
        this.setLayout(new BorderLayout());
        this.setOpaque(false);

        TitledCardPanel section = new TitledCardPanel("Mundos");
        section.setBorder(BorderFactory.createEmptyBorder());
        this.add(section, BorderLayout.CENTER);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        mundoActivoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        tiempoRealLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        lastPlayedLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        previewImageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        previewImageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        previewImageLabel.setVerticalAlignment(SwingConstants.CENTER);
        previewImageLabel.setPreferredSize(new Dimension(320, 320));
        seedField.setEditable(false);
        seedField.setFocusable(true);
        seedField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        seedField.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Dejamos el campo habilitado para que se pueda seleccionar el texto,
        // pero no editable para que no se confunda con un dato modificable.
        seedField.setToolTipText("Puedes seleccionar y copiar la seed.");

        JPanel selectorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        selectorPanel.setOpaque(false);
        selectorPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        selectorPanel.add(new JLabel("Mundos disponibles:"));
        mundosCombo.setPreferredSize(new Dimension(260, 32));
        selectorPanel.add(mundosCombo);
        selectorPanel.add(usarEsteMundoButton);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        botones.setOpaque(false);
        botones.setAlignmentX(Component.LEFT_ALIGNMENT);
        botones.add(importarButton);
        botones.add(exportarButton);
        botones.add(generarButton);
        botones.add(generarPreviewButton);

        styleActionButton(refrescarButton);
        styleActionButton(importarButton);
        styleActionButton(exportarButton);
        styleActionButton(generarButton);
        styleActionButton(generarPreviewButton);
        applyDefaultPrimaryButtonStyle();
        senalarSpawnCheckBox.setOpaque(false);
        senalarSpawnCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        generarTodoCheckBox.setOpaque(false);
        generarTodoCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);

        mundosCombo.addActionListener(e -> {
            if(actualizandoComboMundos) return;
            actualizarVistaMundos();
        });
        refrescarButton.addActionListener(e -> refrescarDatos());
        usarEsteMundoButton.addActionListener(e -> cambiarMundoSeleccionado());
        importarButton.addActionListener(e -> {
            if(GestorMundos.importarMundo(gestorServidores.getServidorSeleccionado(), this)) {
                refrescarDatos();
                notificarCambioDeMundo();
            }
        });
        exportarButton.addActionListener(e -> {
            World mundo = (World) mundosCombo.getSelectedItem();
            if(GestorMundos.exportarMundo(gestorServidores.getServidorSeleccionado(), mundo, this)) {
                refrescarDatos();
            }
        });
        generarButton.addActionListener(e -> abrirDialogoNuevoMundo());
        generarPreviewButton.addActionListener(e -> generarPreviewMundoSeleccionado());

        body.add(mundoActivoLabel);
        body.add(Box.createVerticalStrut(6));
        body.add(tiempoRealLabel);
        body.add(Box.createVerticalStrut(12));
        body.add(new JLabel("Seed:"));
        body.add(Box.createVerticalStrut(4));
        body.add(seedField);
        body.add(Box.createVerticalStrut(12));
        body.add(lastPlayedLabel);
        body.add(Box.createVerticalStrut(12));
        body.add(selectorPanel);
        body.add(Box.createVerticalStrut(12));
        body.add(botones);
        body.add(Box.createVerticalStrut(12));
        body.add(crearPanelPreview());
        body.add(Box.createVerticalStrut(12));
        body.add(Box.createVerticalGlue());

        section.getContentPanel().add(body, BorderLayout.CENTER);

        refrescarDatos();
    }

    private void refrescarDatos(){
        Server server = gestorServidores.getServidorSeleccionado();
        actualizandoComboMundos = true;
        mundosCombo.removeAllItems();
        mundoActivoActual = null;
        mundoTicksCache = "";
        ticksMostradosCache = 0L;
        ticksArchivoCache = 0L;
        ultimaActualizacionTicksMs = System.currentTimeMillis();

        if(server == null){
            mundoActivoLabel.setText("Selecciona un servidor para gestionar sus mundos.");
            tiempoRealLabel.setText("Tiempo activo: -");
            seedField.setText("-");
            lastPlayedLabel.setText("Jugado por ultima vez: -");
            setControlesActivos(false);
            return;
        }

        try {
            GestorMundos.sincronizarMundosServidor(server);
            List<World> mundos = GestorMundos.listarMundos(server);
            mundoActivoActual = GestorMundos.getMundoActivo(server);

            String nombreActivo = mundoActivoActual == null ? "-" : mundoActivoActual.getWorldName();
            mundoActivoLabel.setText("Mundo activo: " + nombreActivo);

            for(World mundo : mundos){
                mundosCombo.addItem(mundo);
            }

            if(!mundos.isEmpty()){
                seleccionarMundoEnCombo(mundoActivoActual);
                if(mundosCombo.getSelectedItem() == null){
                    mundosCombo.setSelectedIndex(0);
                }
            }
            setControlesActivos(true);
        } catch (RuntimeException ex){
            mundoActivoLabel.setText("Error cargando mundos: " + ex.getMessage());
            tiempoRealLabel.setText("Tiempo activo: -");
            seedField.setText("-");
            lastPlayedLabel.setText("Jugado por ultima vez: -");
            setControlesActivos(false);
        } finally {
            actualizandoComboMundos = false;
        }
        actualizarVistaMundos();
    }

    private void actualizarVistaMundos() {
        updateUseWorldButtonState();
        actualizarLabelsDatosServidor();
        actualizarPreviewSeleccionada();
    }

    private void actualizarLabelsDatosServidor(){
        Server server = gestorServidores.getServidorSeleccionado();
        if(server == null){
            mundoActivoLabel.setText("Selecciona un servidor para gestionar sus mundos.");
            tiempoRealLabel.setText("Tiempo activo: -");
            seedField.setText("-");
            lastPlayedLabel.setText("Jugado por ultima vez: -");
            return;
        }

        World mundo = getMundoSeleccionadoOActivo();
        String nombreActivo = mundoActivoActual == null ? "-" : mundoActivoActual.getWorldName();
        mundoActivoLabel.setText("Mundo activo: " + nombreActivo);

        if(mundo == null) {
            tiempoRealLabel.setText("Tiempo activo: -");
            seedField.setText("-");
            lastPlayedLabel.setText("Jugado por ultima vez: -");
            return;
        }

        long ahoraMs = System.currentTimeMillis();
        long ticksArchivo = WorldDataReader.getActiveTicks(mundo);
        String claveMundo = mundo.getWorldName();

        boolean esMismoMundo = Objects.equals(claveMundo, mundoTicksCache);
        boolean servidorActivo = server.getServerProcess() != null && server.getServerProcess().isAlive();

        if(!esMismoMundo) {
            mundoTicksCache = claveMundo;
            ticksArchivoCache = ticksArchivo;
            ticksMostradosCache = ticksArchivo;
            ultimaActualizacionTicksMs = ahoraMs;
        } else if(ticksArchivo > ticksArchivoCache) {
            ticksArchivoCache = ticksArchivo;
            ticksMostradosCache = ticksArchivo;
            ultimaActualizacionTicksMs = ahoraMs;
        } else if(servidorActivo) {
            long deltaMs = Math.max(0L, ahoraMs - ultimaActualizacionTicksMs);
            long deltaTicksEstimados = (deltaMs * 20L) / 1000L;
            if(deltaTicksEstimados > 0L) {
                ticksMostradosCache += deltaTicksEstimados;
                ultimaActualizacionTicksMs = ahoraMs;
            }
        } else {
            ticksMostradosCache = ticksArchivo;
            ticksArchivoCache = ticksArchivo;
            ultimaActualizacionTicksMs = ahoraMs;
        }

        long ticksParaMostrar = Math.max(ticksMostradosCache, 0L);
        // La seed se lee desde level.dat para poder compararla facilmente con visores externos.
        // La mostramos tal cual, sin transformar, porque para depurar previews interesa ver el
        // valor real exacto que Minecraft guarda en el mundo.
        seedField.setText(WorldDataReader.getSeed(mundo));
        lastPlayedLabel.setText("Jugado por última vez: " + WorldDataReader.getLastPlayed(mundo));
        tiempoRealLabel.setText("Tiempo activo: " + formatearTiempo(ticksParaMostrar));
    }

    private World getMundoSeleccionadoOActivo() {
        World mundo = (World) mundosCombo.getSelectedItem();
        return mundo != null ? mundo : mundoActivoActual;
    }

    private String formatearTiempo(long ticks){
        if(ticks <= 0) return "0 s";

        long totalSegundos = ticks / 20L;
        long horas = totalSegundos / 3600L;
        long minutos = (totalSegundos % 3600L) / 60L;
        long segundos = totalSegundos % 60L;

        if(horas > 0) return horas + " h " + minutos + " min " + segundos + " s";
        if(minutos > 0) return minutos + " min " + segundos + " s";
        return segundos + " s";
    }

    private void cambiarMundoSeleccionado(){
        World mundo = (World) mundosCombo.getSelectedItem();
        if(GestorMundos.cambiarMundo(gestorServidores.getServidorSeleccionado(), mundo, this)) {
            refrescarDatos();
            notificarCambioDeMundo();
        }
    }

    private void abrirDialogoNuevoMundo(){
        Server server = gestorServidores.getServidorSeleccionado();
        if(server == null){
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
        if(result != JOptionPane.OK_OPTION) return;

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

        if(GestorMundos.generarNuevoMundo(server, settings, this)) {
            refrescarDatos();
            notificarCambioDeMundo();
        }
    }

    private void seleccionarMundoEnCombo(World objetivo) {
        if(objetivo == null) return;
        for(int i = 0; i < mundosCombo.getItemCount(); i++) {
            World candidato = mundosCombo.getItemAt(i);
            if(candidato != null && Objects.equals(candidato.getWorldName(), objetivo.getWorldName())) {
                mundosCombo.setSelectedIndex(i);
                return;
            }
        }
    }

    private void notificarCambioDeMundo(){
        if(onWorldChanged != null){
            onWorldChanged.run();
        }
    }

    private void setControlesActivos(boolean activos){
        Server server = gestorServidores.getServidorSeleccionado();
        boolean hayServidor = activos && server != null;
        boolean hayMundos = hayServidor && mundosCombo.getItemCount() > 0;

        mundosCombo.setEnabled(hayMundos);
        refrescarButton.setEnabled(hayServidor);
        importarButton.setEnabled(hayServidor);
        exportarButton.setEnabled(hayMundos);
        generarButton.setEnabled(hayServidor);
        generarPreviewButton.setEnabled(hayMundos);
        senalarSpawnCheckBox.setEnabled(hayMundos);
        generarTodoCheckBox.setEnabled(hayMundos);
        updateUseWorldButtonState();
    }

    private void generarPreviewMundoSeleccionado() {
        World mundo = getMundoSeleccionadoOActivo();
        if(mundo == null) {
            JOptionPane.showMessageDialog(this,
                    "No hay un mundo seleccionado.",
                    "Generar preview",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<Path> regionesPreview;
        boolean generarTodo = generarTodoCheckBox.isSelected();
        try {
            regionesPreview = encontrarRegionesPreview(mundo, generarTodo);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "No se han podido localizar regiones .mca para el mundo seleccionado: " + ex.getMessage(),
                    "Generar preview",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if(regionesPreview.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "El mundo no contiene archivos .mca en la carpeta region.",
                    "Generar preview",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        Path outputPath = getPreviewPath(mundo);
        previewImageLabel.setIcon(null);
        previewImageLabel.setText("Generando preview...");
        generarPreviewButton.setEnabled(false);
        MCARenderer renderer = new MCARenderer();
        // Leemos el spawn una sola vez antes de lanzar el worker para que la preview
        // quede ligada al mundo actualmente seleccionado y no a un cambio posterior del combo.
        WorldDataReader.SpawnPoint spawnPoint = senalarSpawnCheckBox.isSelected()
                ? WorldDataReader.getSpawnPoint(mundo)
                : null;
        SwingWorker<Path, Void> worker = new SwingWorker<>() {
            @Override
            protected Path doInBackground() throws Exception {
                MCARenderer.WorldPoint markerPoint = spawnPoint == null
                        ? null
                        : new MCARenderer.WorldPoint(spawnPoint.x(), spawnPoint.z());
                renderer.renderWorldToPng(regionesPreview, outputPath, MCARenderer.RenderOptions.defaults(), markerPoint);
                return outputPath;
            }

            @Override
            protected void done() {
                updateUseWorldButtonState();
                generarPreviewButton.setEnabled(mundosCombo.isEnabled() && getMundoSeleccionadoOActivo() != null);
                try {
                    // get() fuerza a propagar aqui cualquier excepcion ocurrida en segundo plano.
                    // Sin esto, el worker podia fallar internamente y la UI solo mostraba sintomas
                    // vagos como mensajes "null" o una preview que nunca aparecia.
                    get();
                    actualizarPreviewSeleccionada();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    mostrarTextoPreview("La generacion de la preview fue interrumpida.");
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    String message = cause.getMessage();
                    if(message == null || message.isBlank()) {
                        message = cause.getClass().getSimpleName();
                    }
                    mostrarTextoPreview("No se ha podido generar la preview.");
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
        Path worldDir = Path.of(mundo.getWorldDir());
        Path regionDir = worldDir.resolve("region");
        if(!Files.isDirectory(regionDir)) {
            return List.of();
        }

        MCARenderer renderer = new MCARenderer();
        try(Stream<Path> stream = Files.list(regionDir)) {
            List<Path> regiones = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mca"))
                    // Algunos servidores dejan regiones de 0 bytes; no contienen datos validos
                    // y solo sirven para provocar errores de lectura en el renderer.
                    .filter(path -> {
                        try {
                            return Files.size(path) > 0L;
                        } catch (IOException ex) {
                            return false;
                        }
                    })
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();

            if(generarTodo) {
                return regiones;
            }

            List<Path> visibles = new java.util.ArrayList<>();
            for(Path region : regiones) {
                if(renderer.hasVisibleBlocks(region)) {
                    visibles.add(region);
                }
            }
            return visibles.isEmpty() ? regiones : visibles;
        }
    }

    private JPanel crearPanelPreview() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titulo = new JLabel("Preview del mundo");
        titulo.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
        panel.add(titulo, BorderLayout.NORTH);

        previewImageLabel.setBorder(AppTheme.createRoundedBorder(new Insets(12, 12, 12, 12), 1f));
        panel.add(previewImageLabel, BorderLayout.CENTER);

        JPanel opcionesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        opcionesPanel.setOpaque(false);
        opcionesPanel.add(senalarSpawnCheckBox);
        opcionesPanel.add(generarTodoCheckBox);
        panel.add(opcionesPanel, BorderLayout.SOUTH);
        return panel;
    }

    private void actualizarPreviewSeleccionada() {
        World mundo = getMundoSeleccionadoOActivo();
        if(mundo == null) {
            mostrarTextoPreview("La preview todavia no existe.");
            return;
        }

        Path previewPath = getPreviewPath(mundo);
        if(!Files.isRegularFile(previewPath)) {
            mostrarTextoPreview("La preview todavia no existe.");
            return;
        }

        try {
            BufferedImage image = ImageIO.read(previewPath.toFile());
            if(image == null) {
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

    private Image escalarPreview(BufferedImage image, int maxWidth, int maxHeight) {
        int width = image.getWidth();
        int height = image.getHeight();
        if(width <= 0 || height <= 0) {
            return image;
        }

        double ratio = Math.min((double) maxWidth / width, (double) maxHeight / height);
        ratio = Math.min(1.0d, ratio);
        int scaledWidth = Math.max(1, (int) Math.round(width * ratio));
        int scaledHeight = Math.max(1, (int) Math.round(height * ratio));
        return image.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
    }

    private Path getPreviewPath(World mundo) {
        return Path.of(mundo.getWorldDir()).resolve("preview.png");
    }

    private void updateUseWorldButtonState(){
        World seleccionado = (World) mundosCombo.getSelectedItem();
        boolean hayMundoSeleccionado = mundosCombo.isEnabled() && seleccionado != null;
        boolean cambioPendiente = hayMundoSeleccionado
                && mundoActivoActual != null
                && !Objects.equals(seleccionado.getWorldName(), mundoActivoActual.getWorldName());

        applyDefaultPrimaryButtonStyle();
        usarEsteMundoButton.setEnabled(cambioPendiente);
        if(cambioPendiente){
            usarEsteMundoButton.setBackground(AppTheme.getMainAccent());
            usarEsteMundoButton.setForeground(Color.WHITE);
            usarEsteMundoButton.setBorder(AppTheme.createAccentBorder(new Insets(6, 12, 6, 12), 1f));
        }
        usarEsteMundoButton.revalidate();
        usarEsteMundoButton.repaint();
    }

    private void applyDefaultPrimaryButtonStyle(){
        styleActionButton(usarEsteMundoButton);
    }

    private void styleActionButton(JButton button){
        if(button == null) return;
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
}
