package vista;

import controlador.GestorServidores;
import modelo.Server;
import modelo.ServerConfig;
import modelo.MinecraftConstants;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public class PanelConfigServidor extends JPanel {
    private static final String[] DIFFICULTY_OPTIONS = MinecraftConstants.DIFFICULTIES.toArray(String[]::new);
    private static final String[] GAMEMODE_OPTIONS = MinecraftConstants.GAMEMODES.toArray(String[]::new);
    private static final String[] LEVEL_TYPE_OPTIONS = MinecraftConstants.WORLD_TYPES_NAMESPACED.toArray(String[]::new);

    private static final String SECTION_ACCESS_NETWORK = "Acceso y red";
    private static final String SECTION_WORLD_GENERATION = "Mundo y generación";
    private static final String SECTION_GAMEPLAY = "Jugabilidad";
    private static final String SECTION_PERFORMANCE = "Rendimiento";
    private static final String SECTION_MESSAGES_ADVANCED = "Mensajes y avanzado";
    private static final String SECTION_OTHER = "Otros ajustes";

    private static final int RAM_MIN_MB = 512;
    private static final int RAM_MAX_MB = 16384;
    private static final int RAM_STEP_MB = 256;

    private static final Map<String, String> FIELD_LABELS = createFieldLabels();
    private static final Map<String, String> FIELD_DESCRIPTIONS = createFieldDescriptions();

    private static final java.util.List<String> SECTION_ORDER = java.util.List.of(
            SECTION_ACCESS_NETWORK,
            SECTION_WORLD_GENERATION,
            SECTION_GAMEPLAY,
            SECTION_PERFORMANCE,
            SECTION_MESSAGES_ADVANCED,
            SECTION_OTHER
    );

    private enum FieldKind {
        BOOLEAN,
        SELECT,
        NUMBER,
        TEXT,
        TEXTAREA
    }

    private record FieldSpec(String key, String value, FieldKind kind) {}

    private final GestorServidores gestorServidores;
    private final JPanel formPanel;
    private final Map<String, JComponent> editors = new LinkedHashMap<>();
    private Path propertiesPath;
    private JSlider xmsSlider;
    private JSlider xmxSlider;
    private JSpinner xmsSpinner;
    private JSpinner xmxSpinner;
    private JLabel xmsValueLabel;
    private JLabel xmxValueLabel;
    private boolean updatingRamControls;
    private final Map<String, String> persistedValues = new LinkedHashMap<>();
    private int persistedXms;
    private int persistedXmx;
    private final JButton saveButton;

    PanelConfigServidor(GestorServidores gestorServidores){
        this.gestorServidores = gestorServidores;
        this.setLayout(new BorderLayout());
        this.setOpaque(false);

        TitledCardPanel section = new TitledCardPanel("Configuración del servidor");
        section.setBorder(BorderFactory.createEmptyBorder());
        this.add(section, BorderLayout.CENTER);

        JButton recargar = new JButton(tr("panel.config.reload", "Recargar"));
        styleActionButton(recargar);
        saveButton = new JButton(tr("panel.config.save", "Guardar"));
        applyDefaultSaveButtonStyle();
        section.getActionsPanel().add(recargar);
        section.getActionsPanel().add(saveButton);

        formPanel = new JPanel();
        formPanel.setOpaque(false);
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        JScrollPane scroll = new JScrollPane(formPanel);
        scroll.setBorder(null);
        scroll.setOpaque(true);
        scroll.setBackground(AppTheme.getPanelBackground());
        scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(AppTheme.getPanelBackground());
        try{
            JScrollBar v = scroll.getVerticalScrollBar();
            if(v != null){
                v.setBackground(AppTheme.getPanelBackground());
                v.setUnitIncrement(24);
                v.setBlockIncrement(90);
            }
            JScrollBar h = scroll.getHorizontalScrollBar();
            if(h != null){
                h.setBackground(AppTheme.getPanelBackground());
            }
        } catch (Exception ignored){
        }
        section.getContentPanel().add(scroll, BorderLayout.CENTER);

        recargar.addActionListener(e -> reloadWithConfirmation());
        saveButton.addActionListener(e -> save(false));

        SwingUtilities.invokeLater(this::reload);
    }

    void reload(){
        Server server = gestorServidores.getServidorSeleccionado();
        if(server == null || server.getServerDir() == null || server.getServerDir().isBlank()){
            mostrarVacio(tr("panel.config.empty.no_server", "No hay servidor seleccionado."));
            return;
        }

        propertiesPath = Path.of(server.getServerDir()).resolve("server.properties");
        if(!Files.exists(propertiesPath)){
            mostrarVacio(tr("panel.config.empty.missing_properties", "No existe server.properties en: ") + propertiesPath);
            return;
        }

        Properties props = new Properties();
        try(InputStream in = Files.newInputStream(propertiesPath)){
            props.load(in);
        } catch (IOException ex){
            mostrarVacio(tr("panel.config.empty.read_error", "Error leyendo server.properties: ") + ex.getMessage());
            return;
        }

        editors.clear();
        xmsSlider = null;
        xmxSlider = null;
        xmsSpinner = null;
        xmxSpinner = null;
        xmsValueLabel = null;
        xmxValueLabel = null;
        updatingRamControls = false;
        formPanel.removeAll();

        java.util.List<String> keys = new ArrayList<>();
        for(Object k : props.keySet()){
            if(k != null){
                String key = String.valueOf(k);
                if(!isManagedInWorldsPanel(key)){
                    keys.add(key);
                }
            }
        }
        Collections.sort(keys);

        Map<String, java.util.List<String>> groupedKeys = new LinkedHashMap<>();
        for(String sectionTitle : SECTION_ORDER){
            groupedKeys.put(sectionTitle, new ArrayList<>());
        }
        for(String key : keys){
            groupedKeys.computeIfAbsent(categorizeKey(key), ignored -> new ArrayList<>()).add(key);
        }

        boolean first = true;
        for(String sectionTitle : SECTION_ORDER){
            java.util.List<String> sectionKeys = groupedKeys.get(sectionTitle);
            boolean showSection = sectionKeys != null && !sectionKeys.isEmpty();
            if(SECTION_PERFORMANCE.equals(sectionTitle)){
                showSection = true;
            }
            if(!showSection) continue;

            if(!first){
                formPanel.add(Box.createVerticalStrut(12));
            }
            formPanel.add(crearSeccion(sectionTitle, sectionKeys == null ? java.util.List.of() : sectionKeys, props));
            first = false;
        }

        formPanel.add(Box.createVerticalGlue());
        formPanel.revalidate();
        formPanel.repaint();
        markCurrentStateAsPersisted();
        updateSaveButtonState();
    }

    boolean hasUnsavedChanges(){
        if(propertiesPath == null || editors.isEmpty()){
            return false;
        }
        if(!persistedValues.equals(captureCurrentValues())){
            return true;
        }
        return persistedXms != getCurrentXmsValue() || persistedXmx != getCurrentXmxValue();
    }

    boolean confirmDiscardOrSave(Component parent){
        if(!hasUnsavedChanges()){
            return true;
        }

        JOptionPane optionPane = new JOptionPane(
                "¿Quieres guardar los cambios?",
                JOptionPane.WARNING_MESSAGE,
                JOptionPane.DEFAULT_OPTION,
                null,
                new Object[0]
        );

        JButton yesButton = createAccentOptionButton("Sí");
        JButton noButton = createSecondaryOptionButton("No");
        JButton cancelButton = createSecondaryOptionButton("Cancelar");
        optionPane.setOptions(new Object[]{yesButton, noButton, cancelButton});
        optionPane.setInitialValue(yesButton);

        JDialog dialog = optionPane.createDialog(parent != null ? parent : this, tr("panel.config.dialog.title", "CONFIG"));
        dialog.setModal(true);

        yesButton.addActionListener(e -> {
            optionPane.setValue(yesButton);
            dialog.dispose();
        });
        noButton.addActionListener(e -> {
            optionPane.setValue(noButton);
            dialog.dispose();
        });
        cancelButton.addActionListener(e -> {
            optionPane.setValue(cancelButton);
            dialog.dispose();
        });

        dialog.setVisible(true);

        Object result = optionPane.getValue();
        if(result == yesButton){
            return save(false);
        }
        if(result == noButton){
            return true;
        }
        return false;
    }

    private JButton createAccentOptionButton(String text){
        JButton button = createOptionButton(text);
        button.setBackground(AppTheme.getMainAccent());
        button.setForeground(Color.WHITE);
        return button;
    }

    private JButton createSecondaryOptionButton(String text){
        JButton button = createOptionButton(text);
        button.setBackground(AppTheme.getSurfaceBackground());
        button.setForeground(AppTheme.getForeground());
        return button;
    }

    private JButton createOptionButton(String text){
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private void reloadWithConfirmation(){
        if(!confirmDiscardOrSave(this)){
            return;
        }
        reload();
    }

    private boolean save(boolean showSuccessMessage){
        if(propertiesPath == null){
            JOptionPane.showMessageDialog(this, tr("panel.config.save.no_properties", "No hay server.properties cargado."), tr("panel.config.dialog.title", "CONFIG"), JOptionPane.WARNING_MESSAGE);
            return false;
        }

        Map<String, String> currentValues = captureCurrentValues();
        Properties out = new Properties();
        for(Map.Entry<String, String> entry : currentValues.entrySet()){
            out.setProperty(entry.getKey(), entry.getValue());
        }

        try(OutputStream os = Files.newOutputStream(propertiesPath)){
            out.store(os, "Edited by Easy-MC-Server");
        } catch (IOException ex){
            JOptionPane.showMessageDialog(this, tr("panel.config.save.error", "Error guardando: ") + ex.getMessage(), tr("panel.config.dialog.title", "CONFIG"), JOptionPane.ERROR_MESSAGE);
            return false;
        }

        Server server = gestorServidores.getServidorSeleccionado();
        if(server != null){
            ServerConfig config = server.getServerConfig();
            if(config == null){
                config = new ServerConfig();
                server.setServerConfig(config);
            }
            int xms = getCurrentXmsValue(config.getRamInit());
            int xmx = getCurrentXmxValue(config.getRamMax());
            if(xmx < xms){
                xmx = xms;
            }
            config.setRamMax(xmx);
            config.setRamInit(xms);
            gestorServidores.guardarServidor(server);
        }

        markCurrentStateAsPersisted();
        updateSaveButtonState();
        return true;
    }

    private Map<String, String> captureCurrentValues(){
        Map<String, String> values = new LinkedHashMap<>();
        for(Map.Entry<String, JComponent> entry : editors.entrySet()){
            values.put(entry.getKey(), readComponentValue(entry.getValue()));
        }
        return values;
    }

    private String readComponentValue(JComponent comp){
        String value;
        if(comp instanceof JCheckBox cb){
            value = cb.isSelected() ? "true" : "false";
        } else if(comp instanceof JComboBox<?> combo){
            Object selected = combo.getSelectedItem();
            value = selected == null ? "" : String.valueOf(selected);
        } else if(comp instanceof JSpinner spinner){
            Object spinnerValue = spinner.getValue();
            value = spinnerValue == null ? "" : String.valueOf(spinnerValue);
        } else if(comp instanceof JTextArea ta){
            value = ta.getText();
        } else if(comp instanceof JTextField tf){
            value = tf.getText();
        } else {
            value = "";
        }
        return value == null ? "" : value;
    }

    private void markCurrentStateAsPersisted(){
        persistedValues.clear();
        persistedValues.putAll(captureCurrentValues());
        persistedXms = getCurrentXmsValue();
        persistedXmx = getCurrentXmxValue();
    }

    private void updateSaveButtonState(){
        if(saveButton == null) return;
        boolean hasUnsavedChanges = hasUnsavedChanges();
        applyDefaultSaveButtonStyle();
        saveButton.setEnabled(hasUnsavedChanges);
        if(hasUnsavedChanges){
            saveButton.setBackground(AppTheme.getMainAccent());
            saveButton.setForeground(Color.WHITE);
            saveButton.setBorder(AppTheme.createAccentBorder(new Insets(6, 12, 6, 12), 1f));
        }
        saveButton.revalidate();
        saveButton.repaint();
    }

    private void applyDefaultSaveButtonStyle(){
        if(saveButton == null) return;
        styleActionButton(saveButton);
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
        button.setBackground(AppTheme.getSurfaceBackground());
        button.setForeground(AppTheme.getForeground());
    }

    private void attachDirtyTracking(JCheckBox checkBox){
        checkBox.addActionListener(e -> updateSaveButtonState());
    }

    private void attachDirtyTracking(JComboBox<?> combo){
        combo.addActionListener(e -> updateSaveButtonState());
        if(combo.isEditable()){
            Component editor = combo.getEditor().getEditorComponent();
            if(editor instanceof JTextField textField){
                attachDirtyTracking(textField);
            }
        }
    }

    private void attachDirtyTracking(JSpinner spinner){
        spinner.addChangeListener(e -> updateSaveButtonState());
    }

    private void attachDirtyTracking(JTextComponent textComponent){
        textComponent.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateSaveButtonState();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateSaveButtonState();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateSaveButtonState();
            }
        });
    }

    private int getCurrentXmsValue(){
        return getCurrentXmsValue(RAM_MIN_MB);
    }

    private int getCurrentXmxValue(){
        return getCurrentXmxValue(RAM_MIN_MB);
    }

    private int getCurrentXmsValue(int fallback){
        if(xmsSlider != null){
            return xmsSlider.getValue();
        }
        if(xmsSpinner != null){
            return normalizeRamValue(((Number) xmsSpinner.getValue()).intValue());
        }
        return normalizeRamValue(fallback);
    }

    private int getCurrentXmxValue(int fallback){
        if(xmxSlider != null){
            return xmxSlider.getValue();
        }
        if(xmxSpinner != null){
            return normalizeRamValue(((Number) xmxSpinner.getValue()).intValue());
        }
        return normalizeRamValue(fallback);
    }

    private JComponent crearSeccion(String title, java.util.List<String> keys, Properties props){
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setBorder(BorderFactory.createEmptyBorder());
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel sectionPanel = new JPanel();
        sectionPanel.setOpaque(false);
        sectionPanel.setLayout(new BoxLayout(sectionPanel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(tr(sectionTranslationKey(title), title));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        sectionPanel.add(titleLabel);

        JSeparator separator = new JSeparator();
        separator.setForeground(AppTheme.getSubtleBorderColor());
        separator.setAlignmentX(Component.LEFT_ALIGNMENT);
        sectionPanel.add(separator);
        sectionPanel.add(Box.createVerticalStrut(8));

        java.util.List<FieldSpec> booleanFields = new ArrayList<>();
        java.util.List<FieldSpec> otherFields = new ArrayList<>();

        for(String key : keys){
            String value = props.getProperty(key, "");
            FieldSpec spec = new FieldSpec(key, value, detectFieldKind(key, value));
            if(spec.kind() == FieldKind.BOOLEAN){
                booleanFields.add(spec);
            } else {
                otherFields.add(spec);
            }
        }

        boolean needsGap = false;
        needsGap = addBooleanGroup(sectionPanel, booleanFields, needsGap);
        if(SECTION_PERFORMANCE.equals(title)){
            needsGap = addRamGroup(sectionPanel, needsGap);
        }
        addMixedFieldGroup(sectionPanel, otherFields, needsGap);

        wrapper.add(sectionPanel, BorderLayout.NORTH);
        return wrapper;
    }

    private boolean addBooleanGroup(JPanel parent, java.util.List<FieldSpec> specs, boolean needsGapBefore){
        if(specs.isEmpty()) return needsGapBefore;
        if(needsGapBefore){
            parent.add(Box.createVerticalStrut(8));
        }

        JPanel grid = new JPanel(new GridLayout(0, 3, 8, 6));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        for(FieldSpec spec : specs){
            grid.add(crearTarjetaBoolean(spec));
        }

        while(specs.size() % 3 != 0 && grid.getComponentCount() % 3 != 0){
            JPanel filler = new JPanel();
            filler.setOpaque(false);
            grid.add(filler);
        }

        parent.add(grid);
        return true;
    }

    private boolean addRamGroup(JPanel parent, boolean needsGapBefore){
        if(needsGapBefore){
            parent.add(Box.createVerticalStrut(8));
        }
        parent.add(crearTarjetaRam());
        return true;
    }

    private boolean addMixedFieldGroup(JPanel parent, java.util.List<FieldSpec> specs, boolean needsGapBefore){
        if(specs.isEmpty()) return needsGapBefore;
        if(needsGapBefore){
            parent.add(Box.createVerticalStrut(8));
        }

        JPanel group = new JPanel();
        group.setOpaque(false);
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        for(int i = 0; i < specs.size(); i += 2){
            JComponent left = crearTarjetaParaCampo(specs.get(i));
            JComponent right = (i + 1) < specs.size() ? crearTarjetaParaCampo(specs.get(i + 1)) : null;
            group.add(createTwoColumnRow(left, right));
            if(i + 2 < specs.size()){
                group.add(Box.createVerticalStrut(6));
            }
        }

        parent.add(group);
        return true;
    }


    private JPanel createTwoColumnRow(JComponent left, JComponent right){
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        if(right == null){
            row.add(left, BorderLayout.CENTER);
            return row;
        }

        JPanel pair = new JPanel(new GridLayout(1, 2, 8, 0));
        pair.setOpaque(false);
        pair.add(left);
        pair.add(right);
        row.add(pair, BorderLayout.CENTER);
        return row;
    }

    private void applyTooltip(JComponent component, String key){
        if(component == null || key == null) return;
        String tooltip = getFieldDescription(key);
        if(tooltip == null || tooltip.isBlank()) return;
        component.setToolTipText(tooltip);
    }

    private String buildFallbackDescription(String key){
        if(key == null || key.isBlank()) return null;
        return tr("field." + key.toLowerCase(Locale.ROOT) + ".description", "Ajuste avanzado del servidor. Consulta la documentación de Minecraft antes de modificarlo.");
    }

    private String getFieldDescription(String key){
        if(key == null) return null;
        String normalizedKey = key.toLowerCase(Locale.ROOT);
        String fallback = FIELD_DESCRIPTIONS.get(normalizedKey);
        if(fallback == null){
            return buildFallbackDescription(normalizedKey);
        }
        return tr("field." + normalizedKey + ".description", fallback);
    }

    private static String tr(String key, String fallback){
        // Punto unico para futuras traducciones: aqui podra consultarse un ResourceBundle o servicio i18n.
        return fallback;
    }

    private static String sectionTranslationKey(String title){
        if(title == null || title.isBlank()) return "section.unknown";
        return "section." + title.toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static Map<String, String> createFieldLabels(){
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("motd", "MOTD");
        labels.put("server-ip", "IP del servidor");
        labels.put("server-port", "Puerto del servidor");
        labels.put("level-type", "Tipo de mundo");
        labels.put("level-name", "Nombre del mundo");
        labels.put("level-seed", "Semilla del mundo");
        labels.put("max-players", "Jugadores máximos");
        labels.put("view-distance", "Distancia de visión");
        labels.put("simulation-distance", "Distancia de simulación");
        labels.put("white-list", "Whitelist");
        labels.put("online-mode", "Online mode");
        labels.put("enable-rcon", "RCON");
        labels.put("enable-query", "Query");
        labels.put("resource-pack", "Resource pack");
        return labels;
    }

    private static Map<String, String> createFieldDescriptions(){
        Map<String, String> descriptions = new LinkedHashMap<>();
        descriptions.put("motd", "Mensaje del servidor que verán los jugadores en la lista multijugador. Minecraft solo muestra dos líneas.");
        descriptions.put("difficulty", "Dificultad general del mundo: peaceful, easy, normal o hard.");
        descriptions.put("gamemode", "Modo de juego por defecto para los nuevos jugadores que entren al servidor.");
        descriptions.put("level-type", "Tipo de generación del mundo. En versiones actuales suele usarse normal, flat, large_biomes, amplified o single_biome_surface.");
        descriptions.put("level-name", "Nombre de la carpeta del mundo principal del servidor.");
        descriptions.put("level-seed", "Semilla usada para generar el mundo. Déjala vacía para una semilla aleatoria.");
        descriptions.put("generator-settings", "Parámetros adicionales para ciertos tipos de generación de mundo.");
        descriptions.put("generate-structures", "Activa o desactiva la generación de aldeas, templos, fortalezas y otras estructuras.");
        descriptions.put("allow-nether", "Permite viajar y generar el Nether en el servidor.");
        descriptions.put("spawn-protection", "Radio de protección alrededor del spawn donde solo los operadores pueden construir.");
        descriptions.put("max-world-size", "Límite máximo del tamaño del mundo en bloques.");
        descriptions.put("initial-enabled-packs", "Data packs que se activan al crear el mundo por primera vez.");
        descriptions.put("initial-disabled-packs", "Data packs que permanecen desactivados al crear el mundo.");
        descriptions.put("region-file-compression", "Método de compresión usado en los archivos de región del mundo.");

        descriptions.put("server-ip", "IP concreta a la que se enlaza el servidor. Normalmente se deja vacía.");
        descriptions.put("server-port", "Puerto principal del servidor para que los jugadores se conecten.");
        descriptions.put("query.port", "Puerto usado por el sistema de consultas del servidor.");
        descriptions.put("rcon.port", "Puerto de escucha para RCON.");
        descriptions.put("rcon.password", "Contraseña para acceder a RCON.");
        descriptions.put("online-mode", "Comprueba con Mojang que las cuentas sean premium. Desactivarlo reduce la seguridad.");
        descriptions.put("prevent-proxy-connections", "Endurece las comprobaciones para conexiones que usen proxy o VPN.");
        descriptions.put("enable-query", "Activa el protocolo GameSpy4 para consultar estado del servidor.");
        descriptions.put("enable-rcon", "Activa el control remoto del servidor mediante RCON.");
        descriptions.put("hide-online-players", "Oculta el número real de jugadores conectados en la lista multijugador.");
        descriptions.put("white-list", "Solo podrán entrar los jugadores incluidos en la whitelist.");
        descriptions.put("enforce-whitelist", "Expulsa a jugadores no incluidos en la whitelist cuando está activa.");
        descriptions.put("max-players", "Número máximo de jugadores conectados al mismo tiempo.");
        descriptions.put("accepts-transfers", "Permite aceptar transferencias de jugadores desde otros servidores compatibles.");
        descriptions.put("log-ips", "Guarda u oculta las IPs de los jugadores en los registros del servidor.");

        descriptions.put("hardcore", "Si está activado, los jugadores pasan a espectador al morir.");
        descriptions.put("force-gamemode", "Obliga a que los jugadores usen siempre el gamemode configurado al entrar.");
        descriptions.put("pvp", "Permite o bloquea el daño entre jugadores.");
        descriptions.put("allow-flight", "Evita que el servidor expulse jugadores por volar.");
        descriptions.put("spawn-monsters", "Permite que aparezcan monstruos hostiles en el mundo.");
        descriptions.put("spawn-animals", "Permite que aparezcan animales pasivos.");
        descriptions.put("spawn-npcs", "Permite aldeanos y otros NPCs.");
        descriptions.put("player-idle-timeout", "Minutos de inactividad antes de expulsar a un jugador. 0 lo desactiva.");
        descriptions.put("op-permission-level", "Nivel de permisos por defecto para operadores.");
        descriptions.put("function-permission-level", "Nivel de permisos con el que se ejecutan las funciones de data packs.");

        descriptions.put("view-distance", "Distancia de chunks que el servidor envía al cliente.");
        descriptions.put("simulation-distance", "Distancia de chunks donde se actualizan entidades y mecánicas.");
        descriptions.put("network-compression-threshold", "Tamaño mínimo a partir del que la red comprime paquetes.");
        descriptions.put("max-tick-time", "Tiempo máximo de un tick antes de considerar que el servidor se ha colgado.");
        descriptions.put("rate-limit", "Límite de paquetes por segundo por conexión. 0 desactiva el límite.");
        descriptions.put("use-native-transport", "Permite usar las optimizaciones nativas de red de Java cuando están disponibles.");
        descriptions.put("sync-chunk-writes", "Fuerza escrituras síncronas de chunks. Más seguro, pero puede ser más lento.");
        descriptions.put("entity-broadcast-range-percentage", "Ajusta el alcance al que se envían entidades a los clientes.");
        descriptions.put("max-chained-neighbor-updates", "Límite de actualizaciones encadenadas de bloques para evitar cascadas excesivas.");
        descriptions.put("pause-when-empty-seconds", "Tiempo que espera el servidor antes de pausarse si no hay jugadores conectados.");

        descriptions.put("resource-pack", "URL del paquete de recursos que el servidor sugiere o exige descargar.");
        descriptions.put("resource-pack-sha1", "Hash SHA-1 del resource pack para validar su integridad.");
        descriptions.put("resource-pack-id", "Identificador del resource pack usado por versiones modernas.");
        descriptions.put("resource-pack-prompt", "Mensaje mostrado al jugador al pedirle que descargue el resource pack.");
        descriptions.put("require-resource-pack", "Obliga a aceptar el paquete de recursos para entrar.");
        descriptions.put("text-filtering-config", "Configuración externa para filtrar texto y chat.");
        descriptions.put("broadcast-console-to-ops", "Env\u00EDa los mensajes de consola a los operadores conectados.");
        descriptions.put("broadcast-rcon-to-ops", "Env\u00EDa a los operadores los comandos ejecutados mediante RCON.");
        descriptions.put("enable-status", "Permite que el servidor responda a pings de estado desde la lista multijugador.");
        descriptions.put("enable-command-block", "Activa el uso de command blocks en el servidor.");
        descriptions.put("enable-jmx-monitoring", "Activa el monitoreo JMX para herramientas externas.");
        descriptions.put("bug-report-link", "Enlace que el servidor puede mostrar para reportar problemas o incidencias.");
        descriptions.put("debug", "Ajuste avanzado del servidor. Revisa la documentación de Minecraft antes de modificarlo.");
        descriptions.put("announce-player-achievements", "Muestra en el chat determinados logros o avances de los jugadores cuando el servidor los anuncia.");
        descriptions.put("snooper-enabled", "Permite el envío de métricas de uso del servidor.");
        return descriptions;
    }

    private void makeWidthFlexible(JComponent component){
        if(component == null) return;
        Dimension preferred = component.getPreferredSize();
        int preferredHeight = preferred != null ? preferred.height : 24;
        component.setMinimumSize(new Dimension(0, Math.max(preferredHeight, 24)));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(preferredHeight, 24)));
    }

    private JComponent crearTarjetaParaCampo(FieldSpec spec){
        return spec.kind() == FieldKind.NUMBER ? crearTarjetaNumero(spec) : crearTarjetaCampoVertical(spec);
    }

    private JComponent crearTarjetaCampoVertical(FieldSpec spec){
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setOpaque(true);
        card.setBackground(AppTheme.getSurfaceBackground());
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.getSubtleBorderColor(), 1, true),
                BorderFactory.createEmptyBorder(7, 8, 7, 8)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel label = new JLabel(formatLabel(spec.key()));
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        applyTooltip(label, spec.key());
        JComponent editor = crearEditor(spec.key(), spec.value());
        card.add(label, BorderLayout.NORTH);
        card.add(editor, BorderLayout.CENTER);
        makeWidthFlexible(card);
        return card;
    }

    private JComponent crearTarjetaBoolean(FieldSpec spec){
        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(true);
        card.setBackground(AppTheme.getSurfaceBackground());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.getSubtleBorderColor(), 1, true),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));

        JCheckBox checkBox = new JCheckBox(formatLabel(spec.key()));
        checkBox.setOpaque(false);
        checkBox.setSelected("true".equalsIgnoreCase(spec.value()));
        applyTooltip(checkBox, spec.key());
        attachDirtyTracking(checkBox);
        editors.put(spec.key(), checkBox);
        card.add(checkBox, BorderLayout.CENTER);
        makeWidthFlexible(card);
        return card;
    }

    private JComponent crearTarjetaNumero(FieldSpec spec){
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setOpaque(true);
        card.setBackground(AppTheme.getSurfaceBackground());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.getSubtleBorderColor(), 1, true),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));

        JLabel label = new JLabel(formatLabel(spec.key()));
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        applyTooltip(label, spec.key());
        JComponent editor = crearEditor(spec.key(), spec.value());
        card.add(label, BorderLayout.NORTH);
        card.add(editor, BorderLayout.CENTER);
        makeWidthFlexible(card);
        return card;
    }

    private JComponent crearTarjetaRam(){
        JPanel card = new JPanel();
        card.setOpaque(true);
        card.setBackground(AppTheme.getSurfaceBackground());
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.getSubtleBorderColor(), 1, true),
                BorderFactory.createEmptyBorder(7, 8, 7, 8)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        Server server = gestorServidores.getServidorSeleccionado();
        ServerConfig config = server != null ? server.getServerConfig() : null;
        int currentXms = normalizeRamValue(config != null ? config.getRamInit() : 1024);
        int currentXmx = normalizeRamValue(config != null ? config.getRamMax() : 2048);
        if(currentXmx < currentXms){
            currentXmx = currentXms;
        }
        xmsValueLabel = new JLabel();
        xmxValueLabel = new JLabel();
        xmsSlider = createRamSlider(currentXms);
        xmxSlider = createRamSlider(currentXmx);
        xmsSpinner = createRamSpinner(currentXms);
        xmxSpinner = createRamSpinner(currentXmx);

        xmsSlider.addChangeListener(e -> syncRamFromSliders(true));
        xmxSlider.addChangeListener(e -> syncRamFromSliders(false));
        xmsSpinner.addChangeListener(e -> syncRamFromSpinners(true));
        xmxSpinner.addChangeListener(e -> syncRamFromSpinners(false));
        updateRamLabels();

        card.add(crearFilaSlider(tr("panel.config.ram.initial", "RAM Inicial"), xmsSlider, xmsSpinner, xmsValueLabel));
        card.add(Box.createVerticalStrut(6));
        card.add(crearFilaSlider("RAM Máxima", xmxSlider, xmxSpinner, xmxValueLabel));
        return card;
    }

    private JPanel crearFilaSlider(String labelText, JSlider slider, JSpinner spinner, JLabel valueLabel){
        JPanel wrapper = new JPanel(new BorderLayout(12, 6));
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel(labelText);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        label.setPreferredSize(new Dimension(120, 24));
        wrapper.add(label, BorderLayout.WEST);

        JPanel center = new JPanel(new BorderLayout(8, 0));
        center.setOpaque(false);
        center.add(slider, BorderLayout.CENTER);

        JPanel controls = new JPanel(new BorderLayout(6, 0));
        controls.setOpaque(false);
        spinner.setPreferredSize(new Dimension(92, 24));
        controls.add(spinner, BorderLayout.WEST);
        valueLabel.setPreferredSize(new Dimension(28, 24));
        valueLabel.setHorizontalAlignment(SwingConstants.LEFT);
        controls.add(valueLabel, BorderLayout.CENTER);
        center.add(controls, BorderLayout.EAST);
        wrapper.add(center, BorderLayout.CENTER);
        makeWidthFlexible(wrapper);
        return wrapper;
    }

    private JSlider createRamSlider(int value){
        JSlider slider = new JSlider(RAM_MIN_MB, RAM_MAX_MB, normalizeRamValue(value));
        slider.setOpaque(false);
        slider.setMajorTickSpacing(2048);
        slider.setMinorTickSpacing(RAM_STEP_MB);
        slider.setSnapToTicks(true);
        return slider;
    }

    private JSpinner createRamSpinner(int value){
        return new JSpinner(new SpinnerNumberModel(normalizeRamValue(value), RAM_MIN_MB, RAM_MAX_MB, RAM_STEP_MB));
    }

    private void syncRamFromSliders(boolean changedMin){
        if(updatingRamControls || xmsSlider == null || xmxSlider == null || xmsSpinner == null || xmxSpinner == null) return;
        updatingRamControls = true;
        try{
            int xms = xmsSlider.getValue();
            int xmx = xmxSlider.getValue();
            if(xms > xmx){
                if(changedMin){
                    xmx = xms;
                    xmxSlider.setValue(xmx);
                } else {
                    xms = xmx;
                    xmsSlider.setValue(xms);
                }
            }
            xmsSpinner.setValue(xms);
            xmxSpinner.setValue(xmx);
            updateRamLabels();
            updateSaveButtonState();
        } finally {
            updatingRamControls = false;
        }
    }

    private void syncRamFromSpinners(boolean changedMin){
        if(updatingRamControls || xmsSlider == null || xmxSlider == null || xmsSpinner == null || xmxSpinner == null) return;
        updatingRamControls = true;
        try{
            int xms = normalizeRamValue(((Number) xmsSpinner.getValue()).intValue());
            int xmx = normalizeRamValue(((Number) xmxSpinner.getValue()).intValue());
            if(xms > xmx){
                if(changedMin){
                    xmx = xms;
                    xmxSpinner.setValue(xmx);
                } else {
                    xms = xmx;
                    xmsSpinner.setValue(xms);
                }
            }
            xmsSlider.setValue(xms);
            xmxSlider.setValue(xmx);
            updateRamLabels();
            updateSaveButtonState();
        } finally {
            updatingRamControls = false;
        }
    }

    private void updateRamLabels(){
        if(xmsValueLabel != null){
            xmsValueLabel.setText(tr("panel.config.ram.unit", "MB"));
        }
        if(xmxValueLabel != null){
            xmxValueLabel.setText(tr("panel.config.ram.unit", "MB"));
        }
    }

    private int normalizeRamValue(int value){
        int bounded = Math.max(RAM_MIN_MB, Math.min(RAM_MAX_MB, value));
        int remainder = bounded % RAM_STEP_MB;
        if(remainder == 0) return bounded;
        int lower = bounded - remainder;
        int upper = lower + RAM_STEP_MB;
        if(upper > RAM_MAX_MB) return RAM_MAX_MB;
        return (bounded - lower) < (upper - bounded) ? lower : upper;
    }

    private JComponent crearEditor(String key, String value){
        if("difficulty".equalsIgnoreCase(key)){
            JComboBox<String> combo = new JComboBox<>(DIFFICULTY_OPTIONS);
            combo.setSelectedItem(normalizeDifficulty(value));
            attachDirtyTracking(combo);
            editors.put(key, combo);
            makeWidthFlexible(combo);
            return combo;
        }

        if("gamemode".equalsIgnoreCase(key)){
            JComboBox<String> combo = new JComboBox<>(GAMEMODE_OPTIONS);
            combo.setSelectedItem(normalizeGamemode(value));
            attachDirtyTracking(combo);
            editors.put(key, combo);
            makeWidthFlexible(combo);
            return combo;
        }

        if("level-type".equalsIgnoreCase(key)){
            JComboBox<String> combo = new JComboBox<>(LEVEL_TYPE_OPTIONS);
            combo.setEditable(true);
            combo.setSelectedItem(normalizeLevelType(value));
            attachDirtyTracking(combo);
            editors.put(key, combo);
            makeWidthFlexible(combo);
            return combo;
        }

        if(detectFieldKind(key, value) == FieldKind.TEXTAREA){
            JTextArea ta = new JTextArea(value == null ? "" : value, 2, 20);
            ta.setLineWrap(true);
            ta.setWrapStyleWord(true);
            attachDirtyTracking(ta);
            JScrollPane sp = new JScrollPane(ta);
            sp.setBorder(BorderFactory.createLineBorder(AppTheme.getSubtleBorderColor(), 1, true));
            sp.setPreferredSize(new Dimension(200, 52));
            editors.put(key, ta);
            sp.setMinimumSize(new Dimension(0, 52));
            sp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
            return sp;
        }

        if(detectFieldKind(key, value) == FieldKind.NUMBER){
            int numericValue = parseIntegerValue(value);
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(numericValue, Integer.MIN_VALUE, Integer.MAX_VALUE, 1));
            attachDirtyTracking(spinner);
            editors.put(key, spinner);
            makeWidthFlexible(spinner);
            return spinner;
        }

        JTextField tf = new JTextField(value == null ? "" : value);
        attachDirtyTracking(tf);
        editors.put(key, tf);
        makeWidthFlexible(tf);
        return tf;
    }

    private FieldKind detectFieldKind(String key, String value){
        if("difficulty".equalsIgnoreCase(key) || "gamemode".equalsIgnoreCase(key) || "level-type".equalsIgnoreCase(key)){
            return FieldKind.SELECT;
        }
        if("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)){
            return FieldKind.BOOLEAN;
        }
        if("motd".equalsIgnoreCase(key) || (value != null && value.length() > 32)){
            return FieldKind.TEXTAREA;
        }
        if(isNumericKey(key) || isNumericValue(value)){
            return FieldKind.NUMBER;
        }
        return FieldKind.TEXT;
    }

    private boolean isNumericKey(String key){
        if(key == null) return false;
        return switch(key.toLowerCase(Locale.ROOT)){
            case "server-port", "query.port", "rcon.port", "max-players", "spawn-protection",
                 "max-world-size", "player-idle-timeout", "op-permission-level", "function-permission-level",
                 "view-distance", "simulation-distance", "network-compression-threshold", "max-tick-time",
                 "rate-limit", "entity-broadcast-range-percentage" -> true;
            default -> false;
        };
    }

    private boolean isNumericValue(String value){
        if(value == null) return false;
        String trimmed = value.strip();
        return !trimmed.isEmpty() && trimmed.matches("-?\\d+([.]\\d+)?");
    }

    private int parseIntegerValue(String value){
        if(value == null) return 0;
        try{
            if(value.contains(".")){
                return (int) Math.round(Double.parseDouble(value.strip()));
            }
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException ex){
            return 0;
        }
    }

    private String formatLabel(String key){
        if(key == null || key.isBlank()) return "Propiedad";
        if("motd".equalsIgnoreCase(key)) return "MOTD";
        if("server-ip".equalsIgnoreCase(key)) return "IP del servidor";
        if("server-port".equalsIgnoreCase(key)) return "Puerto del servidor";

        String[] parts = key.replace('-', ' ').replace('.', ' ').split("\\s+");
        StringBuilder label = new StringBuilder();
        for(String part : parts){
            if(part.isBlank()) continue;
            if(!label.isEmpty()) label.append(' ');
            label.append(Character.toUpperCase(part.charAt(0)));
            if(part.length() > 1){
                label.append(part.substring(1));
            }
        }
        return label.toString();
    }

    private String categorizeKey(String key){
        if(key == null || key.isBlank()) return SECTION_OTHER;

        return switch(key.toLowerCase(Locale.ROOT)){
            case "server-ip", "server-port", "query.port", "rcon.port", "rcon.password", "online-mode",
                 "prevent-proxy-connections", "enable-query", "enable-rcon", "hide-online-players",
                 "white-list", "enforce-whitelist", "max-players", "accepts-transfers" -> SECTION_ACCESS_NETWORK;

            case "level-name", "level-seed", "level-type", "generator-settings", "generate-structures",
                 "allow-nether", "spawn-protection", "max-world-size", "initial-enabled-packs",
                 "initial-disabled-packs", "region-file-compression" -> SECTION_WORLD_GENERATION;

            case "difficulty", "gamemode", "hardcore", "force-gamemode", "pvp", "allow-flight",
                 "spawn-monsters", "spawn-animals", "spawn-npcs", "player-idle-timeout",
                 "op-permission-level", "function-permission-level" -> SECTION_GAMEPLAY;

            case "view-distance", "simulation-distance", "network-compression-threshold", "max-tick-time",
                 "rate-limit", "use-native-transport", "sync-chunk-writes", "entity-broadcast-range-percentage" -> SECTION_PERFORMANCE;

            case "motd", "resource-pack", "resource-pack-sha1", "resource-pack-id", "resource-pack-prompt",
                 "require-resource-pack", "text-filtering-config", "broadcast-console-to-ops",
                 "broadcast-rcon-to-ops", "enable-status", "enable-command-block", "enable-jmx-monitoring", "bug-report-link" -> SECTION_MESSAGES_ADVANCED;

            default -> SECTION_OTHER;
        };
    }

    private boolean isManagedInWorldsPanel(String key){
        if(key == null) return false;
        return switch(key.toLowerCase(Locale.ROOT)){
            case "level-name", "level-seed", "level-type", "generator-settings", "generate-structures",
                 "allow-nether", "spawn-protection", "max-world-size", "initial-enabled-packs",
                 "initial-disabled-packs", "region-file-compression" -> true;
            default -> false;
        };
    }

    private static String normalizeLevelType(String value){
        if(value == null || value.isBlank()) return LEVEL_TYPE_OPTIONS[0];
        return switch(value.strip().toLowerCase(Locale.ROOT)){
            case "default", MinecraftConstants.WORLD_TYPE_NORMAL, MinecraftConstants.WORLD_TYPE_NORMAL_NAMESPACED -> MinecraftConstants.WORLD_TYPE_NORMAL_NAMESPACED;
            case MinecraftConstants.WORLD_TYPE_FLAT, MinecraftConstants.WORLD_TYPE_FLAT_NAMESPACED -> MinecraftConstants.WORLD_TYPE_FLAT_NAMESPACED;
            case "largebiomes", MinecraftConstants.WORLD_TYPE_LARGE_BIOMES, MinecraftConstants.WORLD_TYPE_LARGE_BIOMES_NAMESPACED -> MinecraftConstants.WORLD_TYPE_LARGE_BIOMES_NAMESPACED;
            case MinecraftConstants.WORLD_TYPE_AMPLIFIED, MinecraftConstants.WORLD_TYPE_AMPLIFIED_NAMESPACED -> MinecraftConstants.WORLD_TYPE_AMPLIFIED_NAMESPACED;
            case "singlebiomesurface", MinecraftConstants.WORLD_TYPE_SINGLE_BIOME_SURFACE, MinecraftConstants.WORLD_TYPE_SINGLE_BIOME_SURFACE_NAMESPACED -> MinecraftConstants.WORLD_TYPE_SINGLE_BIOME_SURFACE_NAMESPACED;
            default -> value.strip();
        };
    }

    private static String normalizeDifficulty(String value){
        if(value == null) return MinecraftConstants.DEFAULT_DIFFICULTY;
        return switch(value.strip().toLowerCase(Locale.ROOT)){
            case "0", MinecraftConstants.DIFFICULTY_PEACEFUL -> MinecraftConstants.DIFFICULTY_PEACEFUL;
            case "1", MinecraftConstants.DIFFICULTY_EASY -> MinecraftConstants.DIFFICULTY_EASY;
            case "2", MinecraftConstants.DIFFICULTY_NORMAL -> MinecraftConstants.DIFFICULTY_NORMAL;
            case "3", MinecraftConstants.DIFFICULTY_HARD -> MinecraftConstants.DIFFICULTY_HARD;
            default -> MinecraftConstants.DEFAULT_DIFFICULTY;
        };
    }

    private static String normalizeGamemode(String value){
        if(value == null) return MinecraftConstants.DEFAULT_GAMEMODE;
        return switch(value.strip().toLowerCase(Locale.ROOT)){
            case "0", MinecraftConstants.GAMEMODE_SURVIVAL -> MinecraftConstants.GAMEMODE_SURVIVAL;
            case "1", MinecraftConstants.GAMEMODE_CREATIVE -> MinecraftConstants.GAMEMODE_CREATIVE;
            case "2", MinecraftConstants.GAMEMODE_ADVENTURE -> MinecraftConstants.GAMEMODE_ADVENTURE;
            case "3", MinecraftConstants.GAMEMODE_SPECTATOR -> MinecraftConstants.GAMEMODE_SPECTATOR;
            default -> MinecraftConstants.DEFAULT_GAMEMODE;
        };
    }

    private void mostrarVacio(String msg){
        editors.clear();
        persistedValues.clear();
        xmsSlider = null;
        xmxSlider = null;
        xmsSpinner = null;
        xmxSpinner = null;
        xmsValueLabel = null;
        xmxValueLabel = null;
        updatingRamControls = false;
        persistedXms = 0;
        persistedXmx = 0;
        formPanel.removeAll();
        updateSaveButtonState();
        JLabel label = new JLabel(msg);
        label.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10));
        formPanel.add(label);
        formPanel.revalidate();
        formPanel.repaint();
    }
}
