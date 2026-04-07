package vista;

import controlador.GestorMundos;
import controlador.GestorServidores;
import controlador.WorldDataReader;
import modelo.MinecraftConstants;
import modelo.Server;
import modelo.World;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Objects;

public class PanelMundo extends JPanel {
    private final GestorServidores gestorServidores;

    private final JLabel mundoActivoLabel = new JLabel();
    private final JLabel tiempoRealLabel = new JLabel("Tiempo activo: -");
    private final JLabel lastPlayedLabel = new JLabel("Nunca");
    private final JComboBox<World> mundosCombo = new JComboBox<>();
    private final JButton refrescarButton = new JButton("Refrescar");
    private final JButton usarEsteMundoButton = new JButton("Usar este mundo");
    private final JButton importarButton = new JButton("Importar mundo");
    private final JButton exportarButton = new JButton("Exportar mundo");
    private final JButton generarButton = new JButton("Generar nuevo mundo");

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

        styleActionButton(refrescarButton);
        styleActionButton(importarButton);
        styleActionButton(exportarButton);
        styleActionButton(generarButton);
        applyDefaultPrimaryButtonStyle();

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

        body.add(mundoActivoLabel);
        body.add(Box.createVerticalStrut(6));
        body.add(tiempoRealLabel);
        body.add(Box.createVerticalStrut(12));
        body.add(lastPlayedLabel);
        body.add(Box.createVerticalStrut(12));
        body.add(selectorPanel);
        body.add(Box.createVerticalStrut(12));
        body.add(botones);
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
    }

    private void actualizarLabelsDatosServidor(){
        Server server = gestorServidores.getServidorSeleccionado();
        if(server == null){
            mundoActivoLabel.setText("Selecciona un servidor para gestionar sus mundos.");
            tiempoRealLabel.setText("Tiempo activo: -");
            lastPlayedLabel.setText("Jugado por ultima vez: -");
            return;
        }

        World mundo = getMundoSeleccionadoOActivo();
        String nombreActivo = mundoActivoActual == null ? "-" : mundoActivoActual.getWorldName();
        mundoActivoLabel.setText("Mundo activo: " + nombreActivo);

        if(mundo == null) {
            tiempoRealLabel.setText("Tiempo activo: -");
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
        updateUseWorldButtonState();
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
