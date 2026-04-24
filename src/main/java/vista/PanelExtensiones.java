package vista;

import com.formdev.flatlaf.extras.components.FlatButton;
import com.formdev.flatlaf.extras.components.FlatScrollPane;
import controlador.extensions.ExtensionCompatibilityReport;
import controlador.extensions.ExtensionCompatibilityStatus;
import controlador.GestorServidores;
import modelo.Server;
import modelo.extensions.ExtensionInstallState;
import modelo.extensions.ExtensionLocalMetadata;
import modelo.extensions.ExtensionSource;
import modelo.extensions.ExtensionSourceType;
import modelo.extensions.ExtensionUpdateState;
import modelo.extensions.ServerEcosystemType;
import modelo.extensions.ServerExtension;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

final class PanelExtensiones extends JPanel {
    private static final String EMPTY_CARD = "empty";
    private static final String DETAILS_CARD = "details";

    private final GestorServidores gestorServidores;
    private final Server server;

    private final DefaultListModel<ServerExtension> extensionsModel = new DefaultListModel<>();
    private final JList<ServerExtension> extensionsList = new JList<>(extensionsModel);
    private final JLabel summaryLabel = new JLabel();
    private final JLabel directoriesLabel = new JLabel();
    private final JLabel detailsTitleLabel = new JLabel("Ninguna extension seleccionada");
    private final JLabel detailsVersionLabel = new JLabel("-");
    private final JLabel detailsAuthorLabel = new JLabel("-");
    private final JLabel detailsStateLabel = new JLabel("-");
    private final JLabel detailsUpdateLabel = new JLabel("-");
    private final JLabel detailsSourceLabel = new JLabel("-");
    private final JLabel detailsRemoteVersionLabel = new JLabel("-");
    private final JLabel detailsFileLabel = new JLabel("-");
    private final JLabel detailsPathLabel = new JLabel("-");
    private final JTextArea descriptionArea = new JTextArea();
    private final JPanel detailsContentCards = new JPanel(new CardLayout());
    private final JLabel placeholderLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel heroIconLabel = new JLabel();
    private final JButton installButton = new FlatButton();
    private final JButton browseCatalogButton = new FlatButton();
    private final JButton removeButton = new FlatButton();
    private final JButton openDirectoryButton = new FlatButton();
    private final CardPanel listCard;
    private final CardPanel detailsCard;
    private final JSplitPane splitPane;
    private boolean loadingExtensions;
    private boolean mutatingExtensions;

    PanelExtensiones(GestorServidores gestorServidores, Server server) {
        this.gestorServidores = gestorServidores;
        this.server = server;

        setLayout(new BorderLayout());
        setOpaque(false);

        listCard = new CardPanel(getSectionLabel(), new Insets(12, 12, 12, 12));
        listCard.setBorder(BorderFactory.createEmptyBorder());
        detailsCard = new CardPanel("Detalles", new Insets(12, 12, 12, 12));
        detailsCard.setBorder(BorderFactory.createEmptyBorder());
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listCard, detailsCard);
        splitPane.setOpaque(false);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setResizeWeight(0.64d);
        splitPane.setDividerLocation(760);
        add(splitPane, BorderLayout.CENTER);

        configurarPanelListado();
        configurarPanelDetalles();
        recargarExtensiones();
    }

    void recargarExtensiones() {
        recargarExtensiones(true);
    }

    private void recargarExtensiones(boolean showLoadingState) {
        listCard.setTitle(getSectionLabel());

        List<Path> directories = gestorServidores.obtenerDirectoriosExtensiones(server);
        boolean supported = gestorServidores.admiteGestionDeExtensiones(server) && !directories.isEmpty();
        String selectedRelativePath = selectedRelativePath();

        if (!supported) {
            loadingExtensions = false;
            mutatingExtensions = false;
            updateActionState();
            extensionsModel.clear();
            summaryLabel.setText("Este servidor no usa una plataforma con extensiones gestionables.");
            directoriesLabel.setText("No hay carpetas de mods o plugins disponibles.");
            mostrarPlaceholderDetalles("Sin gestion de extensiones");
            return;
        }

        loadingExtensions = true;
        if (showLoadingState) {
            summaryLabel.setText("Sincronizando " + getSectionLabel().toLowerCase(Locale.ROOT) + "...");
            directoriesLabel.setText("Carpetas: " + formatDirectories(directories));
            mostrarPlaceholderDetalles("Cargando extensiones...");
        }
        updateActionState();

        new SwingWorker<List<ServerExtension>, Void>() {
            @Override
            protected List<ServerExtension> doInBackground() throws Exception {
                return gestorServidores.sincronizarExtensionesInstaladas(server);
            }

            @Override
            protected void done() {
                loadingExtensions = false;
                updateActionState();
                extensionsModel.clear();
                try {
                    List<ServerExtension> extensions = get();
                    summaryLabel.setText(getSectionLabel() + " instalados: " + extensions.size());
                    directoriesLabel.setText("Carpetas: " + formatDirectories(directories));

                    for (ServerExtension extension : extensions) {
                        extensionsModel.addElement(extension);
                    }
                    ExtensionIconLoader.prefetchIcons(
                            extensionsList,
                            extensions.stream()
                                    .map(ServerExtension::getSource)
                                    .filter(source -> source != null && source.getIconUrl() != null && !source.getIconUrl().isBlank())
                                    .map(ExtensionSource::getIconUrl)
                                    .toList(),
                            40,
                            extensionsList::repaint
                    );

                    if (extensions.isEmpty()) {
                        mostrarPlaceholderDetalles("No hay " + getSectionLabel().toLowerCase(Locale.ROOT) + " instalados");
                        return;
                    }
                    seleccionarExtensionPorRuta(selectedRelativePath);
                } catch (Exception ex) {
                    summaryLabel.setText("No se han podido cargar las extensiones.");
                    directoriesLabel.setText(rootMessage(ex));
                    mostrarPlaceholderDetalles("Error al leer extensiones");
                }
            }
        }.execute();
    }

    private void configurarPanelListado() {
        JButton refreshButton = new FlatButton();
        refreshButton.setToolTipText("Refrescar extensiones");
        AppTheme.applyRefreshIconButtonStyle(refreshButton);
        refreshButton.addActionListener(e -> recargarExtensiones());
        listCard.getHeaderActionsPanel().add(refreshButton);

        installButton.setText("Instalar .jar");
        AppTheme.applyActionButtonStyle(installButton);
        installButton.addActionListener(e -> instalarExtensionManual());

        browseCatalogButton.setText("Explorar catalogo");
        AppTheme.applyAccentButtonStyle(browseCatalogButton);
        browseCatalogButton.addActionListener(e -> abrirMarketplaceExtensiones());

        removeButton.setText("Eliminar");
        AppTheme.applyActionButtonStyle(removeButton);
        removeButton.setEnabled(false);
        removeButton.addActionListener(e -> eliminarExtensionSeleccionada());

        openDirectoryButton.setText("Abrir carpeta");
        AppTheme.applyActionButtonStyle(openDirectoryButton);
        openDirectoryButton.addActionListener(e -> abrirCarpetaExtensiones());

        listCard.getActionsPanel().add(removeButton);
        listCard.getActionsPanel().add(openDirectoryButton);
        listCard.getActionsPanel().add(installButton);
        listCard.getActionsPanel().add(browseCatalogButton);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setOpaque(false);
        listCard.getContentPanel().add(content, BorderLayout.CENTER);

        JPanel infoPanel = new JPanel();
        infoPanel.setOpaque(false);
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        summaryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.BOLD, 15f));
        directoriesLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        directoriesLabel.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 180));
        infoPanel.add(summaryLabel);
        infoPanel.add(Box.createVerticalStrut(6));
        infoPanel.add(directoriesLabel);
        content.add(infoPanel, BorderLayout.NORTH);

        extensionsList.setCellRenderer(new ExtensionCellRenderer());
        extensionsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        extensionsList.setVisibleRowCount(12);
        extensionsList.setOpaque(true);
        extensionsList.setBackground(AppTheme.getPanelBackground());
        extensionsList.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        extensionsList.addListSelectionListener(this::onExtensionSelected);

        FlatScrollPane scroll = new FlatScrollPane();
        scroll.setBorder(null);
        scroll.setOpaque(true);
        scroll.setBackground(AppTheme.getPanelBackground());
        scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(AppTheme.getPanelBackground());
        scroll.setViewportView(extensionsList);
        content.add(scroll, BorderLayout.CENTER);
    }

    private void configurarPanelDetalles() {
        placeholderLabel.setFont(placeholderLabel.getFont().deriveFont(Font.BOLD, 24f));
        placeholderLabel.setForeground(AppTheme.withAlpha(AppTheme.getMainAccent(), 180));

        JPanel emptyPanel = new JPanel(new GridBagLayout());
        emptyPanel.setOpaque(false);
        emptyPanel.add(placeholderLabel);

        JPanel detailsPanel = new JPanel(new BorderLayout(0, 16));
        detailsPanel.setOpaque(false);

        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        heroIconLabel.setIcon(SvgIconFactory.create("easymcicons/box-unselected.svg", 52, 52));
        header.add(heroIconLabel, BorderLayout.WEST);

        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        detailsTitleLabel.setFont(detailsTitleLabel.getFont().deriveFont(Font.BOLD, 17f));
        titleBlock.add(detailsTitleLabel);
        titleBlock.add(Box.createVerticalStrut(4));
        titleBlock.add(detailsVersionLabel);
        titleBlock.add(Box.createVerticalStrut(2));
        titleBlock.add(detailsAuthorLabel);
        header.add(titleBlock, BorderLayout.CENTER);
        detailsPanel.add(header, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(createInfoRow("Estado", detailsStateLabel));
        body.add(Box.createVerticalStrut(8));
        body.add(createInfoRow("Actualizacion", detailsUpdateLabel));
        body.add(Box.createVerticalStrut(8));
        body.add(createInfoRow("Origen", detailsSourceLabel));
        body.add(Box.createVerticalStrut(8));
        body.add(createInfoRow("Version remota", detailsRemoteVersionLabel));
        body.add(Box.createVerticalStrut(8));
        body.add(createInfoRow("Archivo", detailsFileLabel));
        body.add(Box.createVerticalStrut(8));
        body.add(createInfoRow("Ruta", detailsPathLabel));
        body.add(Box.createVerticalStrut(12));

        JLabel descriptionTitle = new JLabel("Descripcion");
        descriptionTitle.setFont(descriptionTitle.getFont().deriveFont(Font.BOLD, 15f));
        descriptionTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(descriptionTitle);
        body.add(Box.createVerticalStrut(6));

        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setOpaque(false);
        descriptionArea.setBorder(null);
        descriptionArea.setFocusable(false);
        descriptionArea.setForeground(AppTheme.getForeground());
        descriptionArea.setFont(descriptionArea.getFont().deriveFont(14f));
        descriptionArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(descriptionArea);
        detailsPanel.add(body, BorderLayout.CENTER);

        detailsContentCards.setOpaque(false);
        detailsContentCards.add(emptyPanel, EMPTY_CARD);
        detailsContentCards.add(detailsPanel, DETAILS_CARD);
        detailsCard.getContentPanel().add(detailsContentCards, BorderLayout.CENTER);
        mostrarPlaceholderDetalles("Selecciona una extension");
    }

    private JPanel createInfoRow(String title, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout(0, 4));
        row.setOpaque(false);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        valueLabel.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 190));
        row.add(titleLabel, BorderLayout.NORTH);
        row.add(valueLabel, BorderLayout.CENTER);
        return row;
    }

    private void onExtensionSelected(ListSelectionEvent event) {
        if (event.getValueIsAdjusting()) {
            return;
        }
        ServerExtension extension = extensionsList.getSelectedValue();
        updateActionState();
        if (extension == null) {
            mostrarPlaceholderDetalles("Selecciona una extension");
            return;
        }
        mostrarDetalles(extension);
    }

    private void mostrarDetalles(ServerExtension extension) {
        if (extension == null) {
            mostrarPlaceholderDetalles("Selecciona una extension");
            return;
        }

        heroIconLabel.setIcon(resolveExtensionIcon(extension, 52));
        detailsTitleLabel.setText(resolveExtensionName(extension));
        detailsVersionLabel.setText("Version " + resolveVersion(extension));
        detailsAuthorLabel.setText(resolveAuthor(extension));
        detailsStateLabel.setText(describeCompatibility(extension).summary());
        detailsUpdateLabel.setText(resolveUpdateState(extension));
        detailsSourceLabel.setText(describeSource(extension.getSource()));
        detailsRemoteVersionLabel.setText(resolveRemoteVersion(extension));
        detailsFileLabel.setText(resolveFileName(extension));
        detailsPathLabel.setText(resolveRelativePath(extension.getLocalMetadata()));
        descriptionArea.setText(resolveDescription(extension, describeCompatibility(extension)));

        ((CardLayout) detailsContentCards.getLayout()).show(detailsContentCards, DETAILS_CARD);
    }

    private void mostrarPlaceholderDetalles(String text) {
        placeholderLabel.setText(text);
        ((CardLayout) detailsContentCards.getLayout()).show(detailsContentCards, EMPTY_CARD);
    }

    private void instalarExtensionManual() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(titleForManualInstall());
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivos JAR", "jar"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File selected = chooser.getSelectedFile();
        if (selected == null) {
            return;
        }

        try {
            ExtensionCompatibilityReport compatibility = gestorServidores.validarCompatibilidadExtension(server, selected.toPath());
            if (compatibility.incompatible()) {
                JOptionPane.showMessageDialog(
                        this,
                        compatibility.summary(),
                        "Instalacion incompatible",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }
            if (compatibility.warning()) {
                int confirm = JOptionPane.showConfirmDialog(
                        this,
                        compatibility.summary() + "\n\n¿Quieres instalar la extension de todas formas?",
                        "Compatibilidad dudosa",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if (confirm != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            mutatingExtensions = true;
            summaryLabel.setText("Instalando extension manual...");
            directoriesLabel.setText(selected.getName());
            updateActionState();

            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    gestorServidores.instalarExtensionManual(server, selected.toPath());
                    return null;
                }

                @Override
                protected void done() {
                    mutatingExtensions = false;
                    updateActionState();
                    try {
                        get();
                        recargarExtensiones(false);
                    } catch (Exception ex) {
                        summaryLabel.setText("No se ha podido instalar la extension.");
                        directoriesLabel.setText(rootMessage(ex));
                        JOptionPane.showMessageDialog(
                                PanelExtensiones.this,
                                "No se ha podido instalar la extension: " + rootMessage(ex),
                                "Extensiones",
                                JOptionPane.ERROR_MESSAGE
                        );
                    }
                }
            }.execute();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "No se ha podido validar la extension: " + ex.getMessage(),
                    "Extensiones",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void eliminarExtensionSeleccionada() {
        ServerExtension extension = extensionsList.getSelectedValue();
        if (extension == null) {
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "¿Seguro que quieres eliminar '" + resolveExtensionName(extension) + "' de este servidor?",
                "Eliminar extension",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        mutatingExtensions = true;
        summaryLabel.setText("Eliminando extension...");
        directoriesLabel.setText(resolveExtensionName(extension));
        updateActionState();

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                gestorServidores.eliminarExtensionLocal(server, extension);
                return null;
            }

            @Override
            protected void done() {
                mutatingExtensions = false;
                updateActionState();
                try {
                    get();
                    recargarExtensiones(false);
                } catch (Exception ex) {
                    summaryLabel.setText("No se ha podido eliminar la extension.");
                    directoriesLabel.setText(rootMessage(ex));
                    JOptionPane.showMessageDialog(
                            PanelExtensiones.this,
                            "No se ha podido eliminar la extension: " + rootMessage(ex),
                            "Extensiones",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        }.execute();
    }

    private void abrirCarpetaExtensiones() {
        List<Path> directories = gestorServidores.obtenerDirectoriosExtensiones(server);
        if (directories.isEmpty()) {
            return;
        }
        try {
            Desktop.getDesktop().open(directories.getFirst().toFile());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "No se ha podido abrir la carpeta de extensiones: " + ex.getMessage(),
                    "Extensiones",
                    JOptionPane.ERROR_MESSAGE
            );
        } catch (UnsupportedOperationException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Este sistema no soporta abrir carpetas desde Java.",
                    "Extensiones",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private String getSectionLabel() {
        return switch (server.getEcosystemType() == null ? ServerEcosystemType.UNKNOWN : server.getEcosystemType()) {
            case MODS -> "Mods";
            case PLUGINS -> "Plugins";
            default -> "Extensiones";
        };
    }

    private String titleForManualInstall() {
        return switch (server.getEcosystemType() == null ? ServerEcosystemType.UNKNOWN : server.getEcosystemType()) {
            case MODS -> "Selecciona un mod (.jar)";
            case PLUGINS -> "Selecciona un plugin (.jar)";
            default -> "Selecciona una extension (.jar)";
        };
    }

    private String formatDirectories(List<Path> directories) {
        return directories.stream()
                .map(path -> path == null ? "" : path.getFileName().toString())
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + ", " + right)
                .orElse("-");
    }

    private String resolveExtensionName(ServerExtension extension) {
        if (extension == null || extension.getDisplayName() == null || extension.getDisplayName().isBlank()) {
            return "Extension";
        }
        return extension.getDisplayName();
    }

    private String resolveVersion(ServerExtension extension) {
        return extension == null || extension.getVersion() == null || extension.getVersion().isBlank()
                ? "sin version"
                : extension.getVersion();
    }

    private String resolveAuthor(ServerExtension extension) {
        ExtensionSource source = extension == null ? null : extension.getSource();
        String author = source == null ? null : source.getAuthor();
        return author == null || author.isBlank() ? "Autor desconocido" : author;
    }

    private String resolveDescription(ServerExtension extension, ExtensionCompatibilityReport compatibility) {
        if (compatibility != null && compatibility.reasons() != null && !compatibility.reasons().isEmpty()) {
            String reasons = compatibility.reasons().stream().reduce((left, right) -> left + "\n- " + right).orElse("");
            String prefix = compatibility.compatible() ? "" : "Compatibilidad:\n- " + reasons + "\n\n";
            String description = baseDescription(extension);
            return prefix + description;
        }
        return baseDescription(extension);
    }

    private String baseDescription(ServerExtension extension) {
        if (extension == null || extension.getDescription() == null || extension.getDescription().isBlank()) {
            return "Sin descripcion disponible para esta extension.";
        }
        return extension.getDescription();
    }

    private String resolveFileName(ServerExtension extension) {
        if (extension == null || extension.getFileName() == null || extension.getFileName().isBlank()) {
            return "-";
        }
        return extension.getFileName();
    }

    private String resolveRelativePath(ExtensionLocalMetadata metadata) {
        if (metadata == null || metadata.getRelativePath() == null || metadata.getRelativePath().isBlank()) {
            return "-";
        }
        return metadata.getRelativePath();
    }

    private String describeSource(ExtensionSource source) {
        if (source == null || source.getType() == null) {
            return "Desconocido";
        }
        return switch (source.getType()) {
            case MANUAL -> "Instalacion manual";
            case LOCAL_FILE -> "Detectado localmente";
            case MODRINTH -> "Modrinth";
            case CURSEFORGE -> "CurseForge";
            case HANGAR -> "Hangar";
            case BUILTIN -> "Incluido";
            default -> "Desconocido";
        };
    }

    private void abrirMarketplaceExtensiones() {
        if (!gestorServidores.admiteGestionDeExtensiones(server)) {
            JOptionPane.showMessageDialog(
                    this,
                    "Este servidor no admite una experiencia de catalogo para extensiones gestionadas.",
                    "Extensiones",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        ExtensionMarketplaceDialog.showDialog(this, gestorServidores, server, this::recargarExtensiones);
    }

    private void updateActionState() {
        List<Path> directories = gestorServidores.obtenerDirectoriosExtensiones(server);
        boolean supported = gestorServidores.admiteGestionDeExtensiones(server) && !directories.isEmpty();
        boolean busy = loadingExtensions || mutatingExtensions;
        installButton.setEnabled(supported && !busy);
        browseCatalogButton.setEnabled(supported && !busy);
        openDirectoryButton.setEnabled(supported && !busy);
        removeButton.setEnabled(supported && !busy && extensionsList.getSelectedValue() != null);
        extensionsList.setEnabled(!busy);
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private String resolveRemoteVersion(ServerExtension extension) {
        ExtensionLocalMetadata metadata = extension == null ? null : extension.getLocalMetadata();
        if (metadata == null || metadata.getKnownRemoteVersion() == null || metadata.getKnownRemoteVersion().isBlank()) {
            return "-";
        }
        return metadata.getKnownRemoteVersion();
    }

    private javax.swing.Icon resolveExtensionIcon(ServerExtension extension, int size) {
        ExtensionSource source = extension == null ? null : extension.getSource();
        String iconUrl = source == null ? null : source.getIconUrl();
        return ExtensionIconLoader.getIcon(iconUrl, size, this::repaint);
    }

    private String resolveUpdateState(ServerExtension extension) {
        ExtensionLocalMetadata metadata = extension == null ? null : extension.getLocalMetadata();
        ExtensionUpdateState updateState = metadata == null ? null : metadata.getUpdateState();
        String message = metadata == null ? null : metadata.getUpdateMessage();
        String base = switch (updateState == null ? ExtensionUpdateState.UNKNOWN : updateState) {
            case UNTRACKED -> "Sin seguimiento remoto";
            case UP_TO_DATE -> "Al dia";
            case UPDATE_AVAILABLE -> "Actualizacion disponible";
            case UNKNOWN -> "Estado remoto desconocido";
        };
        if (message == null || message.isBlank()) {
            return base;
        }
        return base + " - " + message;
    }

    private ExtensionCompatibilityReport describeCompatibility(ServerExtension extension) {
        return gestorServidores.validarCompatibilidadExtension(server, extension);
    }

    private VisualStatus resolveVisualStatus(ServerExtension extension) {
        ExtensionCompatibilityReport compatibility = describeCompatibility(extension);
        if (compatibility.status() == ExtensionCompatibilityStatus.INCOMPATIBLE) {
            return VisualStatus.INCOMPATIBLE;
        }
        if (compatibility.status() == ExtensionCompatibilityStatus.WARNING) {
            return VisualStatus.WARNING;
        }
        return VisualStatus.OK;
    }

    private boolean extensionFileExists(ServerExtension extension) {
        ExtensionLocalMetadata metadata = extension == null ? null : extension.getLocalMetadata();
        if (metadata == null || metadata.getRelativePath() == null || metadata.getRelativePath().isBlank()) {
            return false;
        }
        try {
            Path resolved = Path.of(server.getServerDir()).resolve(metadata.getRelativePath().replace('/', File.separatorChar));
            return Files.isRegularFile(resolved);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean isBasicallyIncompatible(ServerExtension extension) {
        if (extension == null) {
            return false;
        }
        ServerEcosystemType ecosystemType = server.getEcosystemType() == null ? ServerEcosystemType.UNKNOWN : server.getEcosystemType();
        ServerExtensionType extensionType = extension.getExtensionType() == null ? ServerExtensionType.UNKNOWN : extension.getExtensionType();
        if (ecosystemType == ServerEcosystemType.MODS && extensionType == ServerExtensionType.PLUGIN) {
            return true;
        }
        if (ecosystemType == ServerEcosystemType.PLUGINS && extensionType == ServerExtensionType.MOD) {
            return true;
        }
        ServerPlatform extensionPlatform = extension.getPlatform() == null ? ServerPlatform.UNKNOWN : extension.getPlatform();
        ServerPlatform serverPlatform = server.getPlatform() == null ? ServerPlatform.UNKNOWN : server.getPlatform();
        return extensionPlatform != ServerPlatform.UNKNOWN
                && serverPlatform != ServerPlatform.UNKNOWN
                && extensionPlatform != serverPlatform;
    }

    private String selectedRelativePath() {
        ServerExtension selected = extensionsList.getSelectedValue();
        return selected == null || selected.getLocalMetadata() == null
                ? null
                : selected.getLocalMetadata().getRelativePath();
    }

    private void seleccionarExtensionPorRuta(String relativePath) {
        if (extensionsModel.isEmpty()) {
            mostrarPlaceholderDetalles("No hay " + getSectionLabel().toLowerCase(Locale.ROOT) + " instalados");
            return;
        }
        if (relativePath != null) {
            for (int i = 0; i < extensionsModel.size(); i++) {
                ServerExtension extension = extensionsModel.get(i);
                String candidatePath = extension == null || extension.getLocalMetadata() == null
                        ? null
                        : extension.getLocalMetadata().getRelativePath();
                if (relativePath.equals(candidatePath)) {
                    extensionsList.setSelectedIndex(i);
                    mostrarDetalles(extension);
                    return;
                }
            }
        }
        extensionsList.setSelectedIndex(0);
        mostrarDetalles(extensionsModel.getElementAt(0));
    }

    private enum VisualStatus {
        OK,
        WARNING,
        INCOMPATIBLE
    }

    private final class ExtensionCellRenderer implements ListCellRenderer<ServerExtension> {
        @Override
        public Component getListCellRendererComponent(JList<? extends ServerExtension> list,
                                                      ServerExtension value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            JPanel card = new JPanel(new BorderLayout(12, 0));
            card.setOpaque(true);
            card.setBorder(AppTheme.createRoundedBorder(
                    new Insets(10, 10, 10, 10),
                    isSelected ? AppTheme.getMainAccent() : AppTheme.getBorderColor(),
                    1f
            ));
            card.setBackground(isSelected ? AppTheme.getSoftSelectionBackground() : AppTheme.getPanelBackground());

            JLabel icon = new JLabel(resolveExtensionIcon(value, 40));
            card.add(icon, BorderLayout.WEST);

            JPanel textBlock = new JPanel();
            textBlock.setOpaque(false);
            textBlock.setLayout(new BoxLayout(textBlock, BoxLayout.Y_AXIS));

            JLabel nameLabel = new JLabel(resolveExtensionName(value));
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 16f));
            textBlock.add(nameLabel);

            JLabel authorLabel = new JLabel(resolveAuthor(value));
            authorLabel.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 180));
            textBlock.add(authorLabel);

            JLabel versionLabel = new JLabel("Version " + resolveVersion(value));
            versionLabel.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 180));
            textBlock.add(versionLabel);

            card.add(textBlock, BorderLayout.CENTER);
            card.add(createStateBadge(resolveVisualStatus(value)), BorderLayout.EAST);
            return card;
        }

        private JLabel createStateBadge(VisualStatus status) {
            JLabel badge = new JLabel(symbolFor(status), SwingConstants.CENTER);
            badge.setOpaque(true);
            badge.setForeground(Color.WHITE);
            badge.setPreferredSize(new Dimension(36, 36));
            badge.setMinimumSize(new Dimension(36, 36));
            badge.setMaximumSize(new Dimension(36, 36));
            badge.setFont(badge.getFont().deriveFont(Font.BOLD, 22f));
            badge.setBackground(colorFor(status));
            badge.setBorder(BorderFactory.createEmptyBorder());
            return badge;
        }

        private String symbolFor(VisualStatus status) {
            return switch (status == null ? VisualStatus.WARNING : status) {
                case OK -> "✓";
                case WARNING -> "!";
                case INCOMPATIBLE -> "×";
            };
        }

        private Color colorFor(VisualStatus status) {
            return switch (status == null ? VisualStatus.WARNING : status) {
                case OK -> new Color(112, 187, 0);
                case WARNING -> new Color(255, 191, 64);
                case INCOMPATIBLE -> new Color(255, 82, 82);
            };
        }
    }
}
