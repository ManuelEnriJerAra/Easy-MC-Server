package vista;

import controlador.GestorMundos;
import controlador.GestorServidores;
import modelo.Server;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class PanelMundo extends JPanel {
    private final GestorServidores gestorServidores;

    private final JLabel mundoActivoLabel = new JLabel();
    private final JComboBox<String> mundosCombo = new JComboBox<>();
    private final JButton refrescarButton = new JButton("Refrescar");
    private final JButton usarEsteMundoButton = new JButton("Usar este mundo");
    private final JButton importarButton = new JButton("Importar mundo");
    private final JButton exportarButton = new JButton("Exportar mundo");
    private final JButton generarButton = new JButton("Generar nuevo mundo");

    private String mundoActivoActual;
    private final Runnable onWorldChanged;

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

        mundosCombo.addActionListener(e -> updateUseWorldButtonState());
        refrescarButton.addActionListener(e -> refrescarDatos());
        usarEsteMundoButton.addActionListener(e -> cambiarMundoSeleccionado());
        importarButton.addActionListener(e -> {
            if(GestorMundos.importarMundo(gestorServidores.getServidorSeleccionado(), this)) {
                refrescarDatos();
                notificarCambioDeMundo();
            }
        });
        exportarButton.addActionListener(e -> {
            String mundo = (String) mundosCombo.getSelectedItem();
            if(GestorMundos.exportarMundo(gestorServidores.getServidorSeleccionado(), mundo, this)) {
                refrescarDatos();
            }
        });
        generarButton.addActionListener(e -> abrirDialogoNuevoMundo());

        body.add(mundoActivoLabel);
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
        mundosCombo.removeAllItems();
        mundoActivoActual = null;

        if(server == null){
            mundoActivoLabel.setText("Selecciona un servidor para gestionar sus mundos.");
            setControlesActivos(false);
            return;
        }

        try {
            GestorMundos.sincronizarMundosServidor(server);
            List<String> mundos = GestorMundos.listarMundos(server);
            mundoActivoActual = GestorMundos.getNombreMundoActivo(server);

            mundoActivoLabel.setText("Mundo activo: " + mundoActivoActual);

            for(String mundo : mundos){
                mundosCombo.addItem(mundo);
            }

            if(!mundos.isEmpty()){
                mundosCombo.setSelectedItem(mundoActivoActual);
                if(mundosCombo.getSelectedItem() == null){
                    mundosCombo.setSelectedIndex(0);
                }
            }
            setControlesActivos(true);
        } catch (RuntimeException ex){
            mundoActivoLabel.setText("Error cargando mundos: " + ex.getMessage());
            setControlesActivos(false);
        }
    }

    private void cambiarMundoSeleccionado(){
        String mundo = (String) mundosCombo.getSelectedItem();
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

        JTextField nombreField = new JTextField("world-nuevo");
        JTextField semillaField = new JTextField();
        JComboBox<String> tipoBox = new JComboBox<>(new String[]{"normal", "flat", "large biomes", "amplified", "single biome surface"});
        JComboBox<String> gamemodeBox = new JComboBox<>(new String[]{"survival", "creative", "adventure", "spectator"});
        JComboBox<String> dificultadBox = new JComboBox<>(new String[]{"peaceful", "easy", "normal", "hard"});
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
        Object seleccionadoObj = mundosCombo.getSelectedItem();
        String seleccionado = seleccionadoObj instanceof String ? (String) seleccionadoObj : null;
        boolean hayMundoSeleccionado = mundosCombo.isEnabled() && seleccionado != null;
        boolean cambioPendiente = hayMundoSeleccionado
                && mundoActivoActual != null
                && !seleccionado.equals(mundoActivoActual);

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

    private String obtenerNombreServidor(Server server){
        if(server == null) return "";
        if(server.getDisplayName() != null && !server.getDisplayName().isBlank()) {
            return server.getDisplayName();
        }
        return server.getServerDir();
    }
}
