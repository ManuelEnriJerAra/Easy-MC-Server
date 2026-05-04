package vista;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.formdev.flatlaf.extras.components.FlatButton;

import controlador.GestorServidores;
import controlador.extensions.ExtensionCatalogDetails;
import controlador.extensions.ExtensionCatalogEntry;
import controlador.extensions.ExtensionCatalogProviderDescriptor;
import controlador.extensions.ExtensionCatalogQuery;
import controlador.extensions.ExtensionCatalogVersion;
import controlador.extensions.ExtensionCompatibilityStatus;
import controlador.extensions.ExtensionDownloadPlan;
import controlador.extensions.ExtensionInstallResolution;
import controlador.extensions.ExtensionInstallResolutionState;
import modelo.Server;
import modelo.extensions.ServerEcosystemType;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;

final class ExtensionMarketplaceDialog extends JDialog {
    private static final DateTimeFormatter VERSION_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.systemDefault());
    private static final Logger LOGGER = Logger.getLogger(ExtensionMarketplaceDialog.class.getName());

    private final GestorServidores gestorServidores;
    private final Server server;
    private final Runnable onInstallCompleted;

    private final DefaultListModel<MarketplaceEntryViewModel> resultsModel = new DefaultListModel<>();
    private final JList<MarketplaceEntryViewModel> resultsList = new JList<>(resultsModel);
    private final DefaultComboBoxModel<ProviderFilterOption> providerModel = new DefaultComboBoxModel<>();
    private final JComboBox<ProviderFilterOption> providerCombo = new JComboBox<>(providerModel);
    private final DefaultComboBoxModel<PlatformFilterOption> loaderModel = new DefaultComboBoxModel<>();
    private final JComboBox<PlatformFilterOption> loaderCombo = new JComboBox<>(loaderModel);
    private final JTextField searchField = new JTextField();
    private final JTextField versionField = new JTextField();
    private final JCheckBox compatibilityOnlyCheck = new JCheckBox("Solo compatibles", true);
    private final JLabel catalogStatusLabel = new JLabel("Busca una extension para empezar.");
    private final JLabel queueStatusLabel = new JLabel("La cola esta vacia.");
    private final JLabel queueSummaryLabel = new JLabel("Sin descargas pendientes");
    private final DefaultListModel<DownloadQueueItem> queueModel = new DefaultListModel<>();
    private final JList<DownloadQueueItem> queueList = new JList<>(queueModel);
    private final JButton installNowButton = new FlatButton();
    private final JButton queueButton = new FlatButton();
    private final JButton processQueueButton = new FlatButton();
    private final JButton clearFinishedButton = new FlatButton();
    private final JButton loadMoreButton = new FlatButton();
    private final DefaultComboBoxModel<VersionOption> versionModel = new DefaultComboBoxModel<>();
    private final JComboBox<VersionOption> versionCombo = new JComboBox<>(versionModel);

    private final JLabel detailTitleLabel = new JLabel("Selecciona una extension");
    private final JLabel detailSubtitleLabel = new JLabel("-");
    private final JLabel detailStatusBadgeLabel = new JLabel("Sin seleccion");
    private final JLabel detailIconLabel = new JLabel();
    private final JLabel detailProviderLabel = new JLabel("-");
    private final JLabel detailCompatibilityLabel = new JLabel("-");
    private final JLabel detailProjectUrlLabel = new JLabel("-");
    private final JLabel detailLicenseLabel = new JLabel("-");
    private final JLabel detailVersionLabel = new JLabel("-");
    private final JLabel detailFileLabel = new JLabel("-");
    private final JTextArea detailDescriptionArea = new JTextArea();

    private final Timer debounceTimer;
    private ExtensionCatalogEntry selectedEntry;
    private MarketplaceEntryViewModel selectedEntryViewModel;
    private ExtensionCatalogDetails selectedDetails;
    private ExtensionDownloadPlan selectedDownloadPlan;
    private ExtensionInstallResolution selectedInstallResolution;
    private String detailBaseDescription = "Selecciona un resultado para revisar su ficha, sus versiones y su estado en este servidor.";
    private MarketplaceCompatibilityAssessment selectedEntryCompatibility;
    private long searchRequestSequence;
    private long detailRequestSequence;
    private long previewRequestSequence;
    private long installedSyncSequence;
    private SearchViewState searchState = new SearchViewState(ViewState.IDLE, "Busca una extension para empezar.", 0L);
    private DetailViewState detailState = new DetailViewState(ViewState.IDLE, "-", null, 0L);
    private PreviewViewState previewState = new PreviewViewState(ViewState.IDLE, "-", null, 0L);
    private QueueViewState queueState = new QueueViewState(ViewState.EMPTY, "La cola esta vacia.");
    private QueueViewState syncState = new QueueViewState(ViewState.IDLE, "");
    private IconViewState iconState = new IconViewState(ViewState.IDLE, "Sin iconos pendientes");
    private String queueFeedbackHeadline;
    private String queueFeedbackMessage;
    private boolean suppressResultSelectionEvents;
    private boolean suppressVersionSelectionEvents;
    private MarketplaceSearchSpec lastExecutedSearchSpec;
    private int searchLimit = 25;
    private final Map<String, String> providerLabelsById = new HashMap<>();

    static void showDialog(Component parent,
                           GestorServidores gestorServidores,
                           Server server,
                           Runnable onInstallCompleted) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        ExtensionMarketplaceDialog dialog = new ExtensionMarketplaceDialog(owner, gestorServidores, server, onInstallCompleted);
        dialog.setVisible(true);
    }

    private ExtensionMarketplaceDialog(Window owner,
                                       GestorServidores gestorServidores,
                                       Server server,
                                       Runnable onInstallCompleted) {
        super(owner, "Catalogo de extensiones", Dialog.ModalityType.MODELESS);
        this.gestorServidores = gestorServidores;
        this.server = server;
        this.onInstallCompleted = onInstallCompleted;
        this.debounceTimer = new Timer(280, e -> runSearch(false));
        this.debounceTimer.setRepeats(false);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 760));
        setPreferredSize(new Dimension(1280, 820));
        getContentPane().setBackground(AppTheme.getBackground());
        setLayout(new BorderLayout(12, 12));

        add(buildMarketplaceCard(), BorderLayout.CENTER);

        configureFilters();
        configureResultsList();
        configureQueue();
        configureDetails();

        pack();
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(() -> runSearch(true));
    }

    private JComponent buildMarketplaceCard() {
        CardPanel marketplaceCard = new CardPanel("Marketplace");
        marketplaceCard.setBorder(BorderFactory.createEmptyBorder());
        marketplaceCard.getContentPanel().setLayout(new BorderLayout(0, 12));
        marketplaceCard.getContentPanel().add(buildCatalogToolbar(), BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildResultsCard(), buildDetailsCard());
        splitPane.setOpaque(false);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setResizeWeight(0.56d);
        splitPane.setDividerLocation(620);
        splitPane.setContinuousLayout(true);
        splitPane.setDividerSize(8);
        marketplaceCard.getContentPanel().add(splitPane, BorderLayout.CENTER);
        marketplaceCard.getContentPanel().add(buildFooter(), BorderLayout.SOUTH);
        return marketplaceCard;
    }

    private JComponent buildCatalogToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout(0, 10));
        toolbar.setOpaque(false);
        toolbar.add(buildSearchBar(), BorderLayout.NORTH);
        toolbar.add(buildFiltersPanel(), BorderLayout.CENTER);
        return toolbar;
    }

    private JComponent buildFiltersPanel() {
        JPanel filters = new JPanel(new GridBagLayout());
        filters.setOpaque(true);
        filters.setBackground(AppTheme.getSurfaceBackground());
        filters.setBorder(AppTheme.createRoundedBorder(new Insets(8, 8, 8, 8), AppTheme.withAlpha(AppTheme.getBorderColor(), 160), 1f));
        providerCombo.setMinimumSize(new Dimension(170, 38));
        providerCombo.setPreferredSize(new Dimension(190, 38));
        loaderCombo.setMinimumSize(new Dimension(165, 38));
        loaderCombo.setPreferredSize(new Dimension(180, 38));
        versionField.setMinimumSize(new Dimension(110, 38));
        versionField.setPreferredSize(new Dimension(124, 38));
        compatibilityOnlyCheck.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 10);
        gbc.weightx = 0d;
        filters.add(buildInlineFilter("Proveedor", providerCombo), gbc);

        gbc.gridx = 1;
        filters.add(buildInlineFilter("Plataforma", loaderCombo), gbc);

        gbc.gridx = 2;
        filters.add(buildInlineFilter("Version", versionField), gbc);

        gbc.gridx = 3;
        gbc.insets = new Insets(0, 0, 0, 0);
        filters.add(buildToggleFilter("Compatibilidad", compatibilityOnlyCheck), gbc);

        return filters;
    }

    private JComponent buildResultsCard() {
        CardPanel card = new CardPanel("Explorar");
        card.setBorder(BorderFactory.createEmptyBorder());

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);
        card.getContentPanel().add(content, BorderLayout.CENTER);

        catalogStatusLabel.setForeground(AppTheme.getMutedForeground());
        content.add(catalogStatusLabel, BorderLayout.NORTH);

        resultsList.setCellRenderer(new SearchResultRenderer());
        resultsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultsList.setOpaque(false);
        resultsList.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        resultsList.setFixedCellHeight(-1);
        JScrollPane scrollPane = new JScrollPane(resultsList);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(AppTheme.getPanelBackground());
        scrollPane.setBackground(AppTheme.getPanelBackground());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (!e.getValueIsAdjusting()
                    && searchState.state() != ViewState.LOADING
                    && e.getAdjustable().getMaximum() - e.getAdjustable().getVisibleAmount() - e.getValue() < 48) {
                loadMoreResults();
            }
        });
        content.add(scrollPane, BorderLayout.CENTER);

        loadMoreButton.setText("Cargar mas");
        AppTheme.applyActionButtonStyle(loadMoreButton);
        loadMoreButton.setVisible(false);
        loadMoreButton.addActionListener(e -> loadMoreResults());
        content.add(loadMoreButton, BorderLayout.SOUTH);
        return card;
    }

    private JComponent buildDetailsCard() {
        CardPanel card = new CardPanel("Detalle");
        card.setBorder(BorderFactory.createEmptyBorder());

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setOpaque(false);
        card.getContentPanel().add(content, BorderLayout.CENTER);

        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);

        JPanel iconWell = new JPanel(new BorderLayout());
        iconWell.setOpaque(true);
        iconWell.setBackground(AppTheme.getSurfaceBackground());
        iconWell.setBorder(AppTheme.createRoundedBorder(new Insets(10, 10, 10, 10), AppTheme.withAlpha(AppTheme.getBorderColor(), 170), 1f));
        detailIconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        detailIconLabel.setVerticalAlignment(SwingConstants.CENTER);
        detailIconLabel.setIcon(ExtensionIconLoader.getIcon(null, 52, this::repaint));
        iconWell.add(detailIconLabel, BorderLayout.CENTER);
        iconWell.setPreferredSize(new Dimension(76, 76));
        header.add(iconWell, BorderLayout.WEST);

        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        detailTitleLabel.setFont(detailTitleLabel.getFont().deriveFont(Font.BOLD, 20f));
        detailSubtitleLabel.setForeground(AppTheme.getMutedForeground());
        detailSubtitleLabel.setFont(detailSubtitleLabel.getFont().deriveFont(13f));
        titleBlock.add(detailTitleLabel);
        titleBlock.add(Box.createVerticalStrut(6));
        titleBlock.add(detailSubtitleLabel);
        header.add(titleBlock, BorderLayout.CENTER);

        content.add(header, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(buildVersionSelectionPanel());
        body.add(Box.createVerticalStrut(12));
        body.add(buildDescriptionPanel());
        body.add(Box.createVerticalStrut(12));
        body.add(buildProjectLicensePanel());

        detailDescriptionArea.setEditable(false);
        detailDescriptionArea.setOpaque(false);
        detailDescriptionArea.setLineWrap(true);
        detailDescriptionArea.setWrapStyleWord(true);
        detailDescriptionArea.setBorder(null);
        detailDescriptionArea.setFocusable(false);
        detailDescriptionArea.setForeground(AppTheme.getForeground());
        detailDescriptionArea.setFont(detailDescriptionArea.getFont().deriveFont(13.5f));
        detailDescriptionArea.setText(detailBaseDescription);

        JScrollPane bodyScroll = new JScrollPane(body);
        bodyScroll.setBorder(null);
        bodyScroll.getViewport().setOpaque(false);
        bodyScroll.getViewport().setBackground(AppTheme.getPanelBackground());
        bodyScroll.setOpaque(false);
        bodyScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        bodyScroll.getVerticalScrollBar().setUnitIncrement(16);
        content.add(bodyScroll, BorderLayout.CENTER);

        installNowButton.setText("Instalar ahora");
        AppTheme.applyAccentButtonStyle(installNowButton);
        installNowButton.setEnabled(false);
        installNowButton.addActionListener(e -> installSelectionNow());

        queueButton.setText("Agregar a cola");
        AppTheme.applyActionButtonStyle(queueButton);
        queueButton.setEnabled(false);
        queueButton.addActionListener(e -> enqueueSelection());

        card.getActionsPanel().add(queueButton);
        card.getActionsPanel().add(installNowButton);
        return card;
    }

    private JPanel buildProjectLicensePanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(buildInfoRow("Proyecto", detailProjectUrlLabel));
        panel.add(Box.createVerticalStrut(8));
        panel.add(buildInfoRow("Licencia", detailLicenseLabel));
        return panel;
    }

    private JPanel buildVersionSelectionPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(true);
        panel.setBackground(AppTheme.getSurfaceBackground());
        panel.setBorder(AppTheme.createRoundedBorder(new Insets(12, 12, 12, 12), AppTheme.withAlpha(AppTheme.getBorderColor(), 170), 1f));

        JLabel title = new JLabel("Version disponible");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        title.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 225));
        panel.add(title, BorderLayout.NORTH);

        versionCombo.setPreferredSize(new Dimension(0, 38));
        panel.add(versionCombo, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildDescriptionPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(true);
        panel.setBackground(AppTheme.getSurfaceBackground());
        panel.setBorder(AppTheme.createRoundedBorder(new Insets(12, 12, 12, 12), AppTheme.withAlpha(AppTheme.getBorderColor(), 170), 1f));

        JLabel descriptionTitle = new JLabel("Resumen y estado");
        descriptionTitle.setFont(descriptionTitle.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(descriptionTitle, BorderLayout.NORTH);
        panel.add(detailDescriptionArea, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildFooter() {
        CardPanel queueCard = new CardPanel("Instalaciones");
        queueCard.setBorder(BorderFactory.createEmptyBorder());

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setOpaque(false);
        queueCard.getContentPanel().add(content, BorderLayout.CENTER);

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        queueSummaryLabel.setFont(queueSummaryLabel.getFont().deriveFont(Font.BOLD, 13.5f));
        queueSummaryLabel.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 220));
        queueStatusLabel.setForeground(AppTheme.getMutedForeground());
        queueStatusLabel.setFont(queueStatusLabel.getFont().deriveFont(12.5f));
        catalogStatusLabel.setFont(catalogStatusLabel.getFont().deriveFont(12.75f));
        queueStatusLabel.setVerticalAlignment(SwingConstants.TOP);
        header.add(queueSummaryLabel);
        header.add(Box.createVerticalStrut(3));
        header.add(queueStatusLabel);
        content.add(header, BorderLayout.NORTH);

        queueList.setCellRenderer(new QueueItemRenderer());
        queueList.setVisibleRowCount(5);
        queueList.setOpaque(false);
        queueList.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        JScrollPane scrollPane = new JScrollPane(queueList);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(AppTheme.getPanelBackground());
        scrollPane.setPreferredSize(new Dimension(0, 180));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        content.add(scrollPane, BorderLayout.CENTER);

        processQueueButton.setText("Procesar cola");
        AppTheme.applyAccentButtonStyle(processQueueButton);
        processQueueButton.setEnabled(false);
        processQueueButton.addActionListener(e -> processQueue());

        clearFinishedButton.setText("Limpiar historial");
        AppTheme.applyActionButtonStyle(clearFinishedButton);
        clearFinishedButton.setEnabled(false);
        clearFinishedButton.addActionListener(e -> clearFinishedQueueItems());

        queueCard.getActionsPanel().add(clearFinishedButton);
        queueCard.getActionsPanel().add(processQueueButton);
        return queueCard;
    }

    private void configureFilters() {
        loadProviderOptions();
        loadPlatformOptions();

        String serverVersion = server == null || server.getVersion() == null ? "" : server.getVersion();
        versionField.setText(serverVersion);
        searchField.putClientProperty("JTextField.placeholderText", "Buscar por nombre, autor o funcion");
        versionField.putClientProperty("JTextField.placeholderText", serverVersion == null || serverVersion.isBlank() ? "Version" : serverVersion);
        searchField.setToolTipText("Busca extensiones por nombre, autor o descripcion.");
        providerCombo.setToolTipText("Filtra por proveedor compatible con este tipo de servidor.");
        loaderCombo.setToolTipText("Filtra por plataforma o loader relevante para el servidor actual.");
        versionField.setToolTipText("Version de Minecraft a filtrar. Dejala vacia para no forzar una version concreta.");
        compatibilityOnlyCheck.setToolTipText("Muestra solo resultados compatibles con el servidor actual.");
        searchField.setFont(searchField.getFont().deriveFont(13.25f));
        versionField.setFont(versionField.getFont().deriveFont(13.25f));
        providerCombo.setFont(providerCombo.getFont().deriveFont(13f));
        loaderCombo.setFont(loaderCombo.getFont().deriveFont(13f));
        versionCombo.setFont(versionCombo.getFont().deriveFont(13f));
        searchField.setNextFocusableComponent(providerCombo);
        providerCombo.setNextFocusableComponent(loaderCombo);
        loaderCombo.setNextFocusableComponent(versionField);
        versionField.setNextFocusableComponent(compatibilityOnlyCheck);
        searchField.addActionListener(e -> runSearch(true));
        versionField.addActionListener(e -> runSearch(true));
        providerCombo.addActionListener(e -> {
            searchLimit = 25;
            restartDebounce();
        });
        loaderCombo.addActionListener(e -> {
            searchLimit = 25;
            restartDebounce();
        });
        compatibilityOnlyCheck.addActionListener(e -> {
            searchLimit = 25;
            restartDebounce();
        });

        searchField.getDocument().addDocumentListener(SimpleDocumentListener.of(() -> {
            searchLimit = 25;
            restartDebounce();
        }));
        versionField.getDocument().addDocumentListener(SimpleDocumentListener.of(() -> {
            searchLimit = 25;
            restartDebounce();
        }));
    }

    private void configureResultsList() {
        resultsList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || suppressResultSelectionEvents) {
                return;
            }
            MarketplaceEntryViewModel viewModel = resultsList.getSelectedValue();
            if (viewModel == null || viewModel.entry() == null) {
                clearDetails();
                return;
            }
            loadDetails(viewModel);
        });
    }

    private void configureQueue() {
        queueList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }

    private void configureDetails() {
        versionCombo.setRenderer(new VersionRenderer());
        versionCombo.setEnabled(false);
        versionCombo.addActionListener(e -> {
            if (suppressVersionSelectionEvents || selectedEntry == null || versionCombo.getSelectedItem() == null) {
                return;
            }
            previewInstallPlan(selectedEntry, ((VersionOption) versionCombo.getSelectedItem()).versionId());
        });
    }

    private void loadProviderOptions() {
        providerModel.removeAllElements();
        providerLabelsById.clear();
        providerModel.addElement(ProviderFilterOption.all());
        try {
            List<ExtensionCatalogProviderDescriptor> providers = gestorServidores.obtenerRepositoriosExtensiones().stream()
                    .filter(this::providerAllowedForServer)
                    .sorted(Comparator.comparing(ExtensionCatalogProviderDescriptor::displayName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            for (ExtensionCatalogProviderDescriptor provider : providers) {
                providerLabelsById.put(normalized(provider.providerId()), provider.displayName());
                providerModel.addElement(ProviderFilterOption.provider(provider.providerId(), provider.displayName()));
            }
        } catch (RuntimeException ex) {
            LOGGER.log(Level.FINE, "No se ha podido cargar la lista de proveedores del marketplace.", ex);
        }
        providerCombo.setSelectedIndex(0);
    }

    private void loadPlatformOptions() {
        loaderModel.removeAllElements();
        ServerPlatform serverPlatform = server == null || server.getPlatform() == null ? ServerPlatform.UNKNOWN : server.getPlatform();
        loaderModel.addElement(new PlatformFilterOption("Segun servidor (" + labelForPlatform(serverPlatform) + ")", serverPlatform, true));
        loaderModel.addElement(new PlatformFilterOption("Cualquiera", ServerPlatform.UNKNOWN, false));

        List<ServerPlatform> options = switch (server == null || server.getEcosystemType() == null
                ? ServerEcosystemType.UNKNOWN : server.getEcosystemType()) {
            case PLUGINS -> List.of(ServerPlatform.PAPER, ServerPlatform.PURPUR, ServerPlatform.PUFFERFISH, ServerPlatform.SPIGOT, ServerPlatform.BUKKIT);
            case MODS -> List.of(ServerPlatform.FORGE, ServerPlatform.NEOFORGE, ServerPlatform.FABRIC, ServerPlatform.QUILT);
            default -> List.of(ServerPlatform.PAPER, ServerPlatform.SPIGOT, ServerPlatform.FABRIC, ServerPlatform.FORGE);
        };

        for (ServerPlatform option : options) {
            if (option == serverPlatform) {
                continue;
            }
            loaderModel.addElement(new PlatformFilterOption(labelForPlatform(option), option, false));
        }
        loaderCombo.setSelectedIndex(0);
    }

    private void runSearch(boolean force) {
        long requestId = ++searchRequestSequence;
        MarketplaceSearchSpec searchSpec = buildSearchSpec();
        if (!force
                && searchSpec.equals(lastExecutedSearchSpec)
                && (searchState.state() == ViewState.READY || searchState.state() == ViewState.EMPTY)) {
            return;
        }
        searchState = new SearchViewState(ViewState.LOADING, "Buscando extensiones...", requestId);
        iconState = new IconViewState(ViewState.IDLE, "Sin iconos pendientes");
        clearDetails();
        updateFilterState();
        updateCatalogStatusLabel();

        List<MarketplaceEntryViewModel> previousResults = new ArrayList<>();
        for (int i = 0; i < resultsModel.size(); i++) {
            previousResults.add(resultsModel.getElementAt(i));
        }
        String previousSelectionKey = currentSelectedResultKey();
        Map<String, String> queueStateSnapshot = captureQueueStateSnapshot();

        new SwingWorker<List<MarketplaceEntryViewModel>, Void>() {
            @Override
            protected List<MarketplaceEntryViewModel> doInBackground() throws Exception {
                List<ExtensionCatalogEntry> allResults = gestorServidores.buscarExtensionesExternas(searchSpec.query());
                return allResults.stream()
                        .filter(entry -> matchesProviderFilter(entry, searchSpec.provider()))
                        .map(entry -> buildEntryViewModel(entry, queueStateSnapshot))
                        .filter(viewModel -> viewModel != null
                                && (!searchSpec.compatibilityOnly() || viewModel.compatibilityStatus() == ExtensionCompatibilityStatus.COMPATIBLE))
                        .sorted(Comparator
                                .comparingInt((MarketplaceEntryViewModel viewModel) -> compatibilityRank(viewModel.compatibilityStatus()))
                                .thenComparing(viewModel -> defaultString(viewModel.providerLabel(), ""), String.CASE_INSENSITIVE_ORDER)
                                .thenComparing(viewModel -> defaultString(viewModel.entry().displayName(), ""), String.CASE_INSENSITIVE_ORDER))
                        .toList();
            }

            @Override
            protected void done() {
                if (requestId != searchRequestSequence || !isDisplayable()) {
                    return;
                }
                updateFilterState();
                try {
                    List<MarketplaceEntryViewModel> results = get();
                    replaceResultsModel(results);
                    searchState = results.isEmpty()
                            ? new SearchViewState(ViewState.EMPTY, "No hay resultados con los filtros actuales.", requestId)
                            : new SearchViewState(ViewState.READY, results.size() + " resultados listos para revisar.", requestId);
                    lastExecutedSearchSpec = searchSpec;
                    iconState = results.isEmpty()
                            ? new IconViewState(ViewState.EMPTY, "Sin iconos para cargar")
                            : new IconViewState(ViewState.LOADING, "Cargando iconos del listado");
                    ExtensionIconLoader.prefetchIcons(
                            resultsList,
                            results.stream().map(viewModel -> viewModel.entry().iconUrl()).toList(),
                            28,
                            resultsList::repaint
                    );
                    iconState = results.isEmpty()
                            ? new IconViewState(ViewState.EMPTY, "Sin iconos para cargar")
                            : new IconViewState(ViewState.READY, "Iconos cargando en segundo plano");
                    updateCatalogStatusLabel();
                    if (!results.isEmpty()) {
                        restoreResultSelection(previousSelectionKey, true);
                    } else {
                        clearDetails();
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Fallo al ejecutar la busqueda del marketplace.", ex);
                    restoreResults(previousResults);
                    searchState = new SearchViewState(
                            ViewState.ERROR,
                            previousResults.isEmpty()
                                    ? friendlySearchError(ex)
                                    : friendlySearchError(ex) + " Se mantienen los resultados anteriores.",
                            requestId
                    );
                    updateCatalogStatusLabel();
                    if (previousResults.isEmpty()) {
                        clearDetails();
                    }
                }
            }
        }.execute();
    }

    private void loadMoreResults() {
        if (searchState.state() == ViewState.LOADING || resultsModel.getSize() < searchLimit) {
            return;
        }
        searchLimit += 25;
        runSearch(true);
    }

    private void restoreResults(List<MarketplaceEntryViewModel> entries) {
        replaceResultsModel(entries);
        restoreResultSelection(currentSelectedResultKey(), true);
    }

    private void replaceResultsModel(List<MarketplaceEntryViewModel> entries) {
        suppressResultSelectionEvents = true;
        try {
            resultsModel.clear();
            if (entries == null || entries.isEmpty()) {
                return;
            }
            for (MarketplaceEntryViewModel entry : entries) {
                resultsModel.addElement(entry);
            }
        } finally {
            suppressResultSelectionEvents = false;
        }
    }

    private void loadDetails(MarketplaceEntryViewModel viewModel) {
        ExtensionCatalogEntry entry = viewModel == null ? null : viewModel.entry();
        if (entry == null) {
            clearDetails();
            return;
        }
        long requestId = ++detailRequestSequence;
        previewRequestSequence++;
        selectedEntryViewModel = viewModel;
        selectedEntry = entry;
        selectedDetails = null;
        selectedDownloadPlan = null;
        selectedInstallResolution = viewModel.installResolution();
        selectedEntryCompatibility = compatibilityFrom(viewModel);
        versionCombo.setEnabled(false);
        versionModel.removeAllElements();
        detailState = new DetailViewState(ViewState.LOADING, "Cargando detalles y compatibilidad...", entry, requestId);
        previewState = new PreviewViewState(ViewState.IDLE, "-", null, previewRequestSequence);

        detailTitleLabel.setText(entry.displayName());
        detailIconLabel.setIcon(ExtensionIconLoader.getIcon(entry.iconUrl(), 52, this::repaint));
        detailSubtitleLabel.setText(buildDetailSubtitle(entry.author(), entry.providerId()));
        detailProviderLabel.setText(viewModel.providerLabel());
        MarketplaceCompatibilityAssessment compatibility = selectedEntryCompatibility;
        detailCompatibilityLabel.setText(compatibility.summary());
        detailProjectUrlLabel.setText(defaultString(entry.projectUrl(), "Sin enlace disponible"));
        detailLicenseLabel.setText("Sin licencia declarada");
        detailVersionLabel.setText(entry.version() == null ? "-" : entry.version());
        detailFileLabel.setText("Pendiente de resolver");
        detailBaseDescription = cleanDisplayText(entry.description() == null ? "Sin descripcion." : entry.description());
        detailDescriptionArea.setText(detailBaseDescription);
        refreshSelectionActionState();

        ExtensionCatalogQuery query = buildSearchQuery();
        new SwingWorker<Optional<ExtensionCatalogDetails>, Void>() {
            @Override
            protected Optional<ExtensionCatalogDetails> doInBackground() throws Exception {
                return Optional.ofNullable(gestorServidores.obtenerDetalleExtensionExterna(
                        entry.providerId(),
                        entry.projectId(),
                        query
                ));
            }

            @Override
            protected void done() {
                if (requestId != detailRequestSequence || !sameEntry(selectedEntry, entry) || !isDisplayable()) {
                    return;
                }
                try {
                    selectedDetails = get().orElse(null);
                    detailState = new DetailViewState(
                            ViewState.READY,
                            selectedDetails == null
                                    ? "El proveedor no ha devuelto una ficha ampliada. Se muestra la informacion resumida."
                                    : "Detalles cargados.",
                            entry,
                            requestId
                    );
                    populateDetails(entry, selectedDetails);
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Fallo al cargar detalles del marketplace para " + entry.projectId(), ex);
                    detailState = new DetailViewState(ViewState.ERROR, friendlyDetailError(ex), entry, requestId);
                    detailCompatibilityLabel.setText(detailState.message());
                    detailBaseDescription = "La ficha resumida sigue disponible, pero no se ha podido cargar informacion ampliada.\n\n"
                            + friendlyDetailRecoveryHint(ex);
                    detailDescriptionArea.setText(detailBaseDescription);
                    refreshSelectionActionState();
                }
            }
        }.execute();
    }

    private void populateDetails(ExtensionCatalogEntry entry, ExtensionCatalogDetails details) {
        if (!sameEntry(selectedEntry, entry)) {
            return;
        }
        detailTitleLabel.setText(entry.displayName());
        detailIconLabel.setIcon(ExtensionIconLoader.getIcon(entry.iconUrl(), 52, this::repaint));
        detailSubtitleLabel.setText(buildDetailSubtitle(entry.author(), entry.providerId()));
        detailProviderLabel.setText(describeProvider(entry.providerId()));
        detailProjectUrlLabel.setText(details == null || details.websiteUrl() == null
                ? defaultString(entry.projectUrl(), "Sin enlace disponible")
                : details.websiteUrl());
        detailLicenseLabel.setText(details == null
                ? "Sin licencia declarada"
                : defaultString(details.licenseName(), "Sin licencia declarada"));
        detailBaseDescription = buildDetailsDescription(entry, details);
        detailDescriptionArea.setText(detailBaseDescription);

        suppressVersionSelectionEvents = true;
        versionModel.removeAllElements();
        try {
            List<ExtensionCatalogVersion> versions = details == null || details.versions() == null
                    ? List.of()
                    : details.versions().stream()
                    .filter(version -> !compatibilityOnlyCheck.isSelected() || assessVersionCompatibility(version).status() == ExtensionCompatibilityStatus.COMPATIBLE)
                    .sorted(Comparator.comparingLong(ExtensionCatalogVersion::publishedAtEpochMillis).reversed())
                    .toList();

            if (versions.isEmpty()) {
                VersionOption fallback = new VersionOption(
                        defaultString(entry.versionId(), ""),
                        defaultString(entry.version(), "Version por defecto"),
                        "Sin historial de versiones detallado",
                        assessEntryCompatibility(entry).status() == ExtensionCompatibilityStatus.COMPATIBLE
                );
                versionModel.addElement(fallback);
                versionCombo.setEnabled(true);
                versionCombo.setSelectedIndex(0);
                previewInstallPlan(entry, fallback.versionId());
                return;
            }

            for (ExtensionCatalogVersion version : versions) {
                MarketplaceCompatibilityAssessment versionCompatibility = assessVersionCompatibility(version);
                versionModel.addElement(new VersionOption(
                        version.versionId(),
                        defaultString(version.versionNumber(), defaultString(version.displayName(), "Version")),
                        describeVersionMeta(version),
                        versionCompatibility.status() == ExtensionCompatibilityStatus.COMPATIBLE
                ));
            }
            versionCombo.setEnabled(true);
            versionCombo.setSelectedIndex(0);
            detailSubtitleLabel.setText(buildDetailSubtitle(entry.author(), entry.providerId()) + "  |  " + buildAvailableVersionsPreview(versions));
            previewInstallPlan(entry, ((VersionOption) versionCombo.getSelectedItem()).versionId());
        } finally {
            suppressVersionSelectionEvents = false;
        }
    }

    private void previewInstallPlan(ExtensionCatalogEntry entry, String versionId) {
        long requestId = ++previewRequestSequence;
        previewState = new PreviewViewState(ViewState.LOADING, "Resolviendo descarga compatible...", versionId, requestId);
        detailCompatibilityLabel.setText(previewState.message());
        selectedDownloadPlan = null;
        refreshSelectionActionState();

        new SwingWorker<ExtensionDownloadPlan, Void>() {
            @Override
            protected ExtensionDownloadPlan doInBackground() throws Exception {
                return gestorServidores.prepararDescargaExtensionExterna(entry.providerId(), entry.projectId(), versionId, server);
            }

            @Override
            protected void done() {
                if (requestId != previewRequestSequence || !sameEntry(selectedEntry, entry) || !isDisplayable()) {
                    return;
                }
                try {
                    selectedDownloadPlan = get();
                    selectedInstallResolution = selectedDownloadPlan == null
                            ? gestorServidores.evaluarInstalacionExterna(server, entry)
                            : gestorServidores.evaluarInstalacionExterna(server, selectedDownloadPlan);
                    if (selectedDownloadPlan == null || !selectedDownloadPlan.ready()) {
                        previewState = new PreviewViewState(ViewState.BLOCKED, "No hay build compatible para este servidor.", versionId, requestId);
                        detailCompatibilityLabel.setText(previewState.message());
                        detailVersionLabel.setText("-");
                        detailFileLabel.setText("-");
                        refreshSelectionActionState();
                        return;
                    }
                    previewState = new PreviewViewState(
                            ViewState.READY,
                            defaultString(selectedDownloadPlan.message(), "Descarga lista para instalar."),
                            versionId,
                            requestId
                    );
                    detailCompatibilityLabel.setText(previewState.message());
                    detailVersionLabel.setText(defaultString(selectedDownloadPlan.versionNumber(), "-"));
                    detailFileLabel.setText(defaultString(selectedDownloadPlan.fileName(), "-"));
                    refreshSelectionActionState();
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Fallo al preparar la instalacion del marketplace para " + entry.projectId(), ex);
                    previewState = new PreviewViewState(ViewState.ERROR, friendlyPreviewError(ex), versionId, requestId);
                    detailCompatibilityLabel.setText(previewState.message());
                    detailVersionLabel.setText("-");
                    detailFileLabel.setText("-");
                    selectedInstallResolution = gestorServidores.evaluarInstalacionExterna(server, entry);
                    detailBaseDescription = appendRecoveryNote(detailBaseDescription, friendlyPreviewRecoveryHint(ex));
                    refreshSelectionActionState();
                }
            }
        }.execute();
    }

    private void enqueueSelection() {
        if (selectedEntry == null || selectedDownloadPlan == null || !selectedDownloadPlan.ready()) {
            return;
        }
        QueueAdmission admission = evaluateQueueAdmission(selectedEntry, selectedDownloadPlan);
        if (!admission.allowed()) {
            showUserError(admission.message());
            refreshSelectionActionState();
            return;
        }
        if (admission.existingItem() != null) {
            admission.existingItem().state = QueueState.PENDING;
            admission.existingItem().message = "Pendiente de descarga";
            queueList.repaint();
            setQueueFeedback("La descarga ya estaba registrada.", selectedEntry.displayName() + " queda lista para instalarse desde la cola.");
        } else {
            queueModel.addElement(createQueueItem(selectedEntry, selectedDownloadPlan, "Pendiente de descarga"));
            setQueueFeedback("Agregada a la cola.", selectedEntry.displayName() + " espera su turno para instalarse.");
        }
        refreshQueueControls();
        refreshQueuedResultDecorations();
        refreshSelectionActionState();
    }

    private void installSelectionNow() {
        if (selectedEntry == null || selectedDownloadPlan == null || !selectedDownloadPlan.ready()) {
            return;
        }
        QueueAdmission admission = evaluateQueueAdmission(selectedEntry, selectedDownloadPlan);
        if (!admission.allowed()) {
            showUserError(admission.message());
            refreshSelectionActionState();
            return;
        }
        if (admission.existingItem() != null) {
            admission.existingItem().state = QueueState.PENDING;
            admission.existingItem().message = "Instalacion inmediata";
            moveQueueItemToFront(admission.existingItem());
            queueList.repaint();
            setQueueFeedback("La descarga se ha priorizado.", selectedEntry.displayName() + " pasa al principio de la cola.");
        } else {
            queueModel.add(0, createQueueItem(selectedEntry, selectedDownloadPlan, "Instalacion inmediata"));
            setQueueFeedback("Instalacion prioritaria.", selectedEntry.displayName() + " se ha colocado al frente de la cola.");
        }
        refreshQueueControls();
        refreshQueuedResultDecorations();
        refreshSelectionActionState();
        if (queueState.state() != ViewState.LOADING) {
            processQueue();
        }
    }

    private void processQueue() {
        if (queueState.state() == ViewState.LOADING) {
            return;
        }
        DownloadQueueItem next = nextPendingQueueItem();
        if (next == null) {
            refreshQueueControls();
            return;
        }

        queueState = new QueueViewState(ViewState.LOADING, "Procesando cola de descargas...");
        clearQueueFeedback();
        refreshQueueControls();
        next.state = QueueState.DOWNLOADING;
        next.message = "Descargando archivos y preparando instalacion...";
        queueList.repaint();
        setQueueFeedback("Instalando " + next.displayName + "...", "Se esta descargando y validando la extension seleccionada.");
        refreshSelectionActionState();

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                gestorServidores.instalarExtensionExterna(server, next.providerId, next.projectId, next.versionId);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    next.state = QueueState.COMPLETED;
                    next.message = "Instalada correctamente en este servidor";
                    if (selectedEntry == null) {
                        selectedInstallResolution = null;
                    } else if (selectedDownloadPlan != null) {
                        selectedInstallResolution = gestorServidores.evaluarInstalacionExterna(server, selectedDownloadPlan);
                    } else {
                        selectedInstallResolution = gestorServidores.evaluarInstalacionExterna(server, selectedEntry);
                    }
                    if (selectedEntry != null && next.matchesProject(selectedEntry.providerId(), selectedEntry.projectId())) {
                        previewState = new PreviewViewState(ViewState.BLOCKED, "Instalada correctamente en este servidor.", next.versionId, previewRequestSequence);
                    }
                    setQueueFeedback("Instalacion completada.", next.displayName + " ya esta disponible en el servidor.");
                    if (onInstallCompleted != null) {
                        onInstallCompleted.run();
                    }
                } catch (Exception ex) {
                    next.state = QueueState.FAILED;
                    next.message = friendlyQueueFailureMessage(rootMessage(ex));
                    if (selectedEntry != null && next.matchesProject(selectedEntry.providerId(), selectedEntry.projectId())) {
                        previewState = new PreviewViewState(ViewState.ERROR, "No se ha podido instalar: " + next.message, next.versionId, previewRequestSequence);
                    }
                    setQueueFeedback("No se pudo completar la instalacion.", next.message);
                } finally {
                    queueList.repaint();
                    refreshQueueControls();
                    refreshResultDecorationsAsync();
                    refreshSelectionActionState();
                    SwingUtilities.invokeLater(() -> {
                        if (nextPendingQueueItem() != null) {
                            processQueue();
                        }
                    });
                }
            }
        }.execute();
    }

    private void clearFinishedQueueItems() {
        int removed = 0;
        List<DownloadQueueItem> retained = new ArrayList<>();
        for (int i = 0; i < queueModel.size(); i++) {
            DownloadQueueItem item = queueModel.get(i);
            if (item.state == QueueState.PENDING || item.state == QueueState.DOWNLOADING) {
                retained.add(item);
            } else {
                removed++;
            }
        }
        queueModel.clear();
        for (DownloadQueueItem item : retained) {
            queueModel.addElement(item);
        }
        if (removed > 0) {
            setQueueFeedback("Cola limpiada.", removed == 1
                    ? "Se ha retirado una descarga resuelta."
                    : "Se han retirado " + removed + " descargas resueltas.");
        }
        refreshQueueControls();
        refreshQueuedResultDecorations();
        refreshSelectionActionState();
    }

    private void refreshInstalledExtensions() {
        if (syncState.state() == ViewState.LOADING) {
            return;
        }
        long requestId = ++installedSyncSequence;
        syncState = new QueueViewState(ViewState.LOADING, "Sincronizando extensiones instaladas...");
        clearQueueFeedback();
        queueStatusLabel.setText("Sincronizando extensiones instaladas...");
        refreshQueueControls();
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                gestorServidores.sincronizarExtensionesInstaladas(server);
                gestorServidores.buscarActualizacionesExtensiones(server);
                return null;
            }

            @Override
            protected void done() {
                if (requestId != installedSyncSequence || !isDisplayable()) {
                    return;
                }
                try {
                    get();
                    syncState = new QueueViewState(ViewState.READY, "Extensiones instaladas sincronizadas.");
                    if (onInstallCompleted != null) {
                        onInstallCompleted.run();
                    }
                    setQueueFeedback("Sincronizacion completada.", "La informacion local del marketplace vuelve a estar al dia.");
                    refreshResultDecorationsAsync();
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Fallo al sincronizar extensiones instaladas.", ex);
                    syncState = new QueueViewState(ViewState.ERROR, friendlySyncError(ex));
                    setQueueFeedback("No se pudo sincronizar.", friendlySyncRecoveryHint(ex));
                } finally {
                    refreshQueueControls();
                }
            }
        }.execute();
    }

    private void refreshQueueControls() {
        boolean hasItems = queueModel.getSize() > 0;
        boolean hasPending = nextPendingQueueItem() != null;
        boolean hasResolvedItems = hasResolvedQueueItems();
        processQueueButton.setEnabled(hasPending && queueState.state() != ViewState.LOADING && syncState.state() != ViewState.LOADING);
        clearFinishedButton.setEnabled(hasResolvedItems);

        if (syncState.state() == ViewState.LOADING) {
            queueSummaryLabel.setText("Sincronizando biblioteca");
            queueStatusLabel.setText(syncState.message());
            return;
        }
        if (queueState.state() == ViewState.LOADING) {
            queueSummaryLabel.setText("Instalacion en curso");
            queueStatusLabel.setText(queueState.message());
            return;
        }
        if (!hasItems) {
            queueState = new QueueViewState(ViewState.EMPTY, "La cola esta vacia.");
            clearQueueFeedback();
            queueSummaryLabel.setText("Sin descargas pendientes");
            queueStatusLabel.setText("Agrega extensiones a la cola o usa la instalacion directa para priorizarlas.");
            return;
        }
        long completed = 0L;
        long failed = 0L;
        long pending = 0L;
        for (int i = 0; i < queueModel.size(); i++) {
            DownloadQueueItem item = queueModel.get(i);
            if (item.state == QueueState.COMPLETED) {
                completed++;
            } else if (item.state == QueueState.FAILED) {
                failed++;
            } else if (item.state == QueueState.PENDING) {
                pending++;
            }
        }
        queueState = new QueueViewState(ViewState.READY,
                "Pendientes: " + pending + "  |  Completadas: " + completed + "  |  Fallidas: " + failed);
        queueSummaryLabel.setText(defaultString(queueFeedbackHeadline, buildQueueHeadline(pending, completed, failed)));
        queueStatusLabel.setText(defaultString(queueFeedbackMessage, buildQueueSecondaryMessage(pending, completed, failed)));
    }

    private DownloadQueueItem nextPendingQueueItem() {
        for (int i = 0; i < queueModel.getSize(); i++) {
            DownloadQueueItem item = queueModel.get(i);
            if (item.state == QueueState.PENDING) {
                return item;
            }
        }
        return null;
    }

    private boolean hasResolvedQueueItems() {
        for (int i = 0; i < queueModel.getSize(); i++) {
            QueueState state = queueModel.get(i).state;
            if (state == QueueState.COMPLETED || state == QueueState.FAILED) {
                return true;
            }
        }
        return false;
    }

    private void setQueueFeedback(String headline, String message) {
        queueFeedbackHeadline = headline;
        queueFeedbackMessage = message;
    }

    private void clearQueueFeedback() {
        queueFeedbackHeadline = null;
        queueFeedbackMessage = null;
    }

    private String buildQueueHeadline(long pending, long completed, long failed) {
        if (failed > 0L && pending == 0L) {
            return failed == 1L ? "Hay 1 instalacion con incidencia" : "Hay " + failed + " instalaciones con incidencia";
        }
        if (pending > 0L) {
            return pending == 1L ? "1 instalacion pendiente" : pending + " instalaciones pendientes";
        }
        if (completed > 0L) {
            return completed == 1L ? "1 instalacion completada" : completed + " instalaciones completadas";
        }
        return "Cola actualizada";
    }

    private String buildQueueSecondaryMessage(long pending, long completed, long failed) {
        if (failed > 0L && pending > 0L) {
            return "Se mantiene la cola activa. Revisa las instalaciones fallidas cuando termine el resto.";
        }
        if (pending > 0L) {
            return "La cola esta lista para instalarse en orden y sin duplicados.";
        }
        if (failed > 0L) {
            return "Puedes reintentar una descarga fallida volviendo a agregar esa extension desde el marketplace.";
        }
        if (completed > 0L) {
            return "La cola no tiene pendientes. Puedes limpiar las instalaciones ya resueltas cuando quieras.";
        }
        return "Sin actividad reciente en la cola.";
    }

    private String friendlyQueueFailureMessage(String rawMessage) {
        String message = defaultString(rawMessage, "No se pudo completar la descarga o instalacion.");
        if (message.contains("ya esta instalada")) {
            return "La extension ya estaba instalada en el servidor.";
        }
        if (message.contains("No se ha podido resolver una descarga compatible")) {
            return "No se encontro una descarga compatible con este servidor.";
        }
        if (message.contains("URL de descarga valida")) {
            return "El proveedor no ha devuelto una descarga valida.";
        }
        if (message.contains("formato ZIP/JAR") || message.contains("corrupto") || message.contains("no contiene entradas")) {
            return "La descarga recibida no parece un archivo .jar valido.";
        }
        if (message.contains("interrumpida")) {
            return "La descarga se interrumpio antes de completarse.";
        }
        if (message.contains("HTTP") || message.contains("consultar")) {
            return "No se ha podido contactar con el proveedor remoto en este momento.";
        }
        return message;
    }

    private void updateFilterState() {
        providerCombo.setEnabled(true);
        loaderCombo.setEnabled(true);
        versionField.setEnabled(true);
        searchField.setEnabled(true);
        compatibilityOnlyCheck.setEnabled(true);
        loadMoreButton.setEnabled(searchState.state() != ViewState.LOADING && resultsModel.getSize() >= searchLimit);
    }

    private void updateCatalogStatusLabel() {
        if (searchState.state() == ViewState.READY && resultsModel.getSize() > 0) {
            catalogStatusLabel.setText(searchState.message() + "  |  " + iconState.message());
            loadMoreButton.setVisible(resultsModel.getSize() >= searchLimit);
            return;
        }
        if (searchState.state() == ViewState.ERROR && !resultsModel.isEmpty()) {
            catalogStatusLabel.setText(searchState.message() + "  |  Puedes seguir revisando los resultados ya cargados.");
            loadMoreButton.setVisible(resultsModel.getSize() >= searchLimit);
            return;
        }
        catalogStatusLabel.setText(searchState.message());
        loadMoreButton.setVisible(false);
    }

    private void refreshSelectionActionState() {
        ActionAvailability availability = evaluateActionAvailability();
        installNowButton.setEnabled(availability.canInstallNow());
        queueButton.setEnabled(availability.canQueue());
        if (selectedEntry != null) {
            MarketplaceCompatibilityAssessment compatibility = selectedEntryCompatibility != null
                    ? selectedEntryCompatibility
                    : assessEntryCompatibility(selectedEntry);
            if (previewState.state() == ViewState.ERROR || detailState.state() == ViewState.ERROR) {
                detailCompatibilityLabel.setText(defaultString(availability.detailMessage(), "No se ha podido validar esta accion."));
                detailCompatibilityLabel.setForeground(AppTheme.getDangerColor());
            } else if (previewState.state() == ViewState.LOADING || detailState.state() == ViewState.LOADING) {
                detailCompatibilityLabel.setText(defaultString(availability.detailMessage(), "Cargando estado..."));
                detailCompatibilityLabel.setForeground(AppTheme.getMainAccent());
            } else if (previewState.state() == ViewState.BLOCKED || (selectedInstallResolution != null && selectedInstallResolution.blocksInstall())) {
                detailCompatibilityLabel.setText(defaultString(availability.detailMessage(), buildCompatibilityDisplayText(compatibility)));
                detailCompatibilityLabel.setForeground(AppTheme.getWarningColor());
            } else {
                detailCompatibilityLabel.setText(buildCompatibilityDisplayText(compatibility));
                detailCompatibilityLabel.setForeground(compatibilityBadgeForeground(compatibility.status()));
            }
        }
        updatePrimaryActionLabels();
        updateDetailVisualState(availability.detailMessage());
    }

    private void updatePrimaryActionLabels() {
        ExtensionInstallResolution resolution = selectedInstallResolution;
        if (selectedEntry == null) {
            installNowButton.setText("Instalar ahora");
            queueButton.setText("Agregar a cola");
            return;
        }
        if (resolution == null) {
            installNowButton.setText("Instalar ahora");
            queueButton.setText("Agregar a cola");
            return;
        }
        installNowButton.setText(switch (resolution.state()) {
            case INSTALLED_EXACT -> "Ya instalada";
            case UPDATE_AVAILABLE -> "Hay actualizacion";
            case FILE_NAME_CONFLICT -> "Conflicto detectado";
            case INSTALLED_WITH_INCOMPLETE_METADATA -> "Revisar instalada";
            case AVAILABLE -> "Instalar ahora";
        });
        queueButton.setText(resolution.state() == ExtensionInstallResolutionState.AVAILABLE
                ? "Agregar a cola"
                : "Revisar estado");
    }

    private ActionAvailability evaluateActionAvailability() {
        if (selectedEntry == null) {
            return new ActionAvailability(false, false, detailState.message());
        }
        if (syncState.state() == ViewState.LOADING) {
            return new ActionAvailability(false, false, "Se esta sincronizando la biblioteca instalada del servidor.");
        }
        if (detailState.state() == ViewState.LOADING) {
            return new ActionAvailability(false, false, detailState.message());
        }
        if (previewState.state() == ViewState.LOADING) {
            return new ActionAvailability(false, false, previewState.message());
        }

        MarketplaceCompatibilityAssessment compatibility = selectedEntryCompatibility != null
                ? selectedEntryCompatibility
                : assessEntryCompatibility(selectedEntry);
        if (compatibility.status() == ExtensionCompatibilityStatus.INCOMPATIBLE) {
            return new ActionAvailability(false, false, compatibility.summary());
        }
        ExtensionInstallResolution resolution = selectedInstallResolution;
        if (resolution != null && resolution.blocksInstall()) {
            return new ActionAvailability(false, false, defaultString(resolution.message(), previewState.message()));
        }

        QueueAdmission queueAdmission = evaluateQueueAdmission(selectedEntry, selectedDownloadPlan);
        if (!queueAdmission.allowed()) {
            boolean exactQueued = queueAdmission.existingItem() != null
                    && queueAdmission.existingItem().matchesExactVersion(selectedEntry.providerId(), selectedEntry.projectId(),
                    selectedDownloadPlan == null ? null : selectedDownloadPlan.versionId());
            return new ActionAvailability(false, false, exactQueued
                    ? "Esta version ya esta en cola."
                    : queueAdmission.message());
        }
        if (selectedDownloadPlan == null || !selectedDownloadPlan.ready()) {
            return new ActionAvailability(false, false, defaultString(previewState.message(), compatibility.summary()));
        }
        return new ActionAvailability(true, true, defaultString(previewState.message(), compatibility.summary()));
    }

    private QueueAdmission evaluateQueueAdmission(ExtensionCatalogEntry entry, ExtensionDownloadPlan downloadPlan) {
        if (entry == null) {
            return new QueueAdmission(false, null, "Selecciona una extension.");
        }
        if (downloadPlan == null || !downloadPlan.ready()) {
            return new QueueAdmission(false, null, "Todavia no hay una descarga compatible preparada.");
        }
        ExtensionInstallResolution resolution = selectedInstallResolution != null
                ? selectedInstallResolution
                : gestorServidores.evaluarInstalacionExterna(server, downloadPlan);
        if (resolution != null && resolution.blocksInstall()) {
            return new QueueAdmission(false, null, resolution.message());
        }

        DownloadQueueItem exactFailed = null;
        for (int i = 0; i < queueModel.getSize(); i++) {
            DownloadQueueItem item = queueModel.get(i);
            if (!item.matchesProject(entry.providerId(), entry.projectId())) {
                continue;
            }
            if (item.state == QueueState.FAILED && item.matchesExactVersion(entry.providerId(), entry.projectId(), downloadPlan.versionId())) {
                exactFailed = item;
                continue;
            }
            if (item.state == QueueState.PENDING || item.state == QueueState.DOWNLOADING) {
                String message = item.matchesExactVersion(entry.providerId(), entry.projectId(), downloadPlan.versionId())
                        ? "Esta version ya esta en cola."
                        : "Ya hay otra version de esta extension en cola.";
                return new QueueAdmission(false, item, message);
            }
        }
        return new QueueAdmission(true, exactFailed, null);
    }

    private DownloadQueueItem createQueueItem(ExtensionCatalogEntry entry,
                                              ExtensionDownloadPlan downloadPlan,
                                              String message) {
        return new DownloadQueueItem(
                entry.providerId(),
                entry.projectId(),
                downloadPlan.versionId(),
                entry.displayName(),
                defaultString(downloadPlan.versionNumber(), defaultString(entry.version(), "version")),
                QueueState.PENDING,
                message
        );
    }

    private void moveQueueItemToFront(DownloadQueueItem target) {
        if (target == null) {
            return;
        }
        int index = queueModel.indexOf(target);
        if (index <= 0) {
            return;
        }
        queueModel.remove(index);
        queueModel.add(0, target);
    }

    private boolean sameEntry(ExtensionCatalogEntry left, ExtensionCatalogEntry right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return normalized(left.providerId()) != null
                && normalized(left.providerId()).equalsIgnoreCase(defaultString(right.providerId(), ""))
                && normalized(left.projectId()) != null
                && normalized(left.projectId()).equalsIgnoreCase(defaultString(right.projectId(), ""));
    }

    private void showUserError(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        setQueueFeedback("Accion no disponible.", message);
        refreshQueueControls();
        JOptionPane.showMessageDialog(
                this,
                message,
                "Marketplace",
                JOptionPane.WARNING_MESSAGE
        );
    }

    private String describeQueuedState(ExtensionCatalogEntry entry) {
        if (entry == null) {
            return null;
        }
        for (int i = 0; i < queueModel.size(); i++) {
            DownloadQueueItem item = queueModel.get(i);
            if (!item.matchesProject(entry.providerId(), entry.projectId())) {
                continue;
            }
            return switch (item.state) {
                case PENDING -> "En cola";
                case DOWNLOADING -> "Instalando";
                case FAILED -> "Error en cola";
                case COMPLETED -> "Instalada";
            };
        }
        return null;
    }

    private MarketplaceEntryViewModel buildEntryViewModel(ExtensionCatalogEntry entry) {
        return buildEntryViewModel(entry, captureQueueStateSnapshot());
    }

    private MarketplaceEntryViewModel buildEntryViewModel(ExtensionCatalogEntry entry, Map<String, String> queueStateSnapshot) {
        if (entry == null) {
            return null;
        }
        MarketplaceCompatibilityAssessment compatibility = assessEntryCompatibility(entry);
        ExtensionInstallResolution resolution = gestorServidores.evaluarInstalacionExterna(server, entry);
        return new MarketplaceEntryViewModel(
                entry,
                compatibility.status(),
                compatibility.summary(),
                compatibility.reasons(),
                resolution,
                queueStateSnapshot == null ? null : queueStateSnapshot.get(entryKey(entry)),
                describeProvider(entry.providerId()),
                summarizePlatforms(entry),
                summarizeVersions(entry),
                trimToLength(normalizeInlineText(defaultString(entry.description(), "Sin descripcion.")), 210)
        );
    }

    private MarketplaceCompatibilityAssessment compatibilityFrom(MarketplaceEntryViewModel viewModel) {
        if (viewModel == null) {
            return new MarketplaceCompatibilityAssessment(
                    ExtensionCompatibilityStatus.WARNING,
                    "Compatibilidad sin datos suficientes",
                    List.of("No hay informacion suficiente del proveedor para evaluar esta extension.")
            );
        }
        return new MarketplaceCompatibilityAssessment(
                viewModel.compatibilityStatus(),
                defaultString(viewModel.compatibilitySummary(), "Compatibilidad sin validar"),
                viewModel.compatibilityReasons() == null ? List.of() : viewModel.compatibilityReasons()
        );
    }

    private String currentSelectedResultKey() {
        if (selectedEntryViewModel != null) {
            return selectedEntryViewModel.key();
        }
        return entryKey(selectedEntry);
    }

    private void restoreResultSelection(String preferredKey, boolean loadDetailsAfterSelection) {
        if (resultsModel.isEmpty()) {
            return;
        }
        int targetIndex = 0;
        if (preferredKey != null) {
            for (int i = 0; i < resultsModel.size(); i++) {
                if (preferredKey.equals(resultsModel.get(i).key())) {
                    targetIndex = i;
                    break;
                }
            }
        }
        suppressResultSelectionEvents = true;
        try {
            resultsList.setSelectedIndex(targetIndex);
        } finally {
            suppressResultSelectionEvents = false;
        }
        MarketplaceEntryViewModel selected = resultsModel.get(targetIndex);
        if (loadDetailsAfterSelection) {
            loadDetails(selected);
            return;
        }
        selectedEntryViewModel = selected;
        selectedEntry = selected.entry();
        selectedEntryCompatibility = compatibilityFrom(selected);
        String selectedKey = entryKey(selectedEntry);
        if (selectedInstallResolution != null && selectedKey != null && selectedKey.equals(selected.key())) {
            selectedInstallResolution = selected.installResolution();
        }
        refreshSelectionActionState();
    }

    private void refreshQueuedResultDecorations() {
        if (resultsModel.isEmpty()) {
            return;
        }
        String selectedKey = currentSelectedResultKey();
        for (int i = 0; i < resultsModel.size(); i++) {
            MarketplaceEntryViewModel current = resultsModel.get(i);
            String newQueueState = describeQueuedState(current.entry());
            if ((current.queueStateText() == null && newQueueState == null)
                    || (current.queueStateText() != null && current.queueStateText().equals(newQueueState))) {
                continue;
            }
            MarketplaceEntryViewModel updated = new MarketplaceEntryViewModel(
                    current.entry(),
                    current.compatibilityStatus(),
                    current.compatibilitySummary(),
                    current.compatibilityReasons(),
                    current.installResolution(),
                    newQueueState,
                    current.providerLabel(),
                    current.platformsSummary(),
                    current.versionsSummary(),
                    current.descriptionPreview()
            );
            resultsModel.set(i, updated);
            if (selectedKey != null && selectedKey.equals(updated.key())) {
                selectedEntryViewModel = updated;
            }
        }
        resultsList.repaint();
    }

    private void refreshResultDecorationsAsync() {
        if (resultsModel.isEmpty()) {
            return;
        }
        List<ExtensionCatalogEntry> entries = new ArrayList<>();
        for (int i = 0; i < resultsModel.size(); i++) {
            entries.add(resultsModel.get(i).entry());
        }
        String selectedKey = currentSelectedResultKey();
        Map<String, String> queueStateSnapshot = captureQueueStateSnapshot();
        new SwingWorker<List<MarketplaceEntryViewModel>, Void>() {
            @Override
            protected List<MarketplaceEntryViewModel> doInBackground() {
                return entries.stream()
                        .map(entry -> buildEntryViewModel(entry, queueStateSnapshot))
                        .toList();
            }

            @Override
            protected void done() {
                if (!isDisplayable()) {
                    return;
                }
                try {
                    List<MarketplaceEntryViewModel> refreshed = get();
                    replaceResultsModel(refreshed);
                    restoreResultSelection(selectedKey, false);
                } catch (Exception ex) {
                    LOGGER.log(Level.FINE, "No se ha podido refrescar la decoracion del listado del marketplace.", ex);
                }
            }
        }.execute();
    }

    private Map<String, String> captureQueueStateSnapshot() {
        Map<String, String> snapshot = new HashMap<>();
        for (int i = 0; i < queueModel.size(); i++) {
            DownloadQueueItem item = queueModel.get(i);
            String key = entryKey(item.providerId, item.projectId);
            if (key == null) {
                continue;
            }
            snapshot.put(key, switch (item.state) {
                case PENDING -> "En cola";
                case DOWNLOADING -> "Instalando";
                case FAILED -> "Error en cola";
                case COMPLETED -> "Instalada";
            });
        }
        return snapshot;
    }

    private String entryKey(ExtensionCatalogEntry entry) {
        if (entry == null) {
            return null;
        }
        return entryKey(entry.providerId(), entry.projectId());
    }

    private String entryKey(String providerId, String projectId) {
        String provider = normalized(providerId);
        String project = normalized(projectId);
        if (provider == null || project == null) {
            return null;
        }
        return provider.toLowerCase(Locale.ROOT) + "::" + project.toLowerCase(Locale.ROOT);
    }

    private void clearDetails() {
        detailRequestSequence++;
        previewRequestSequence++;
        selectedEntryViewModel = null;
        selectedEntry = null;
        selectedEntryCompatibility = null;
        selectedDetails = null;
        selectedDownloadPlan = null;
        selectedInstallResolution = null;
        detailState = new DetailViewState(ViewState.IDLE, "-", null, detailRequestSequence);
        previewState = new PreviewViewState(ViewState.IDLE, "-", null, previewRequestSequence);
        detailTitleLabel.setText("Selecciona una extension");
        detailSubtitleLabel.setText("-");
        detailIconLabel.setIcon(ExtensionIconLoader.getIcon(null, 52, this::repaint));
        detailProviderLabel.setText("-");
        detailCompatibilityLabel.setText("-");
        detailCompatibilityLabel.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 220));
        detailProjectUrlLabel.setText("Sin enlace disponible");
        detailLicenseLabel.setText("Sin licencia declarada");
        detailBaseDescription = "Selecciona un resultado para ver sus detalles antes de instalar.";
        detailDescriptionArea.setText(detailBaseDescription);
        detailVersionLabel.setText("-");
        detailFileLabel.setText("-");
        versionModel.removeAllElements();
        versionCombo.setEnabled(false);
        refreshSelectionActionState();
    }

    private JPanel buildField(String title, JComponent component) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createVerticalStrut(6));
        panel.add(component);
        return panel;
    }

    private JComponent buildSearchBar() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(true);
        panel.setBackground(AppTheme.getSurfaceBackground());
        panel.setBorder(AppTheme.createRoundedBorder(new Insets(8, 12, 8, 10), AppTheme.withAlpha(AppTheme.getBorderColor(), 160), 1f));

        searchField.setMinimumSize(new Dimension(260, 38));
        searchField.setPreferredSize(new Dimension(360, 38));
        searchField.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        searchField.setOpaque(false);
        panel.add(searchField, BorderLayout.CENTER);

        JButton searchButton = new JButton();
        AppTheme.applyHeaderIconButtonStyle(searchButton);
        searchButton.setIcon(SvgIconFactory.create("easymcicons/magnifier.svg", 24, 24, AppTheme::getForeground));
        searchButton.setToolTipText("Buscar");
        searchButton.setPreferredSize(new Dimension(36, 36));
        searchButton.addActionListener(e -> runSearch(true));
        panel.add(searchButton, BorderLayout.EAST);
        return panel;
    }

    private JComponent buildInlineFilter(String title, JComponent component) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder());
        panel.add(createFilterCaption(title), BorderLayout.WEST);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildToggleFilter(String title, JCheckBox checkBox) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder());
        panel.add(createFilterCaption(title));
        panel.add(checkBox);
        return panel;
    }

    private JLabel createFilterCaption(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12.5f));
        label.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 185));
        return label;
    }

    private JPanel buildInfoRow(String title, JLabel value) {
        JPanel row = new JPanel(new BorderLayout(0, 4));
        row.setOpaque(false);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        value.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 200));
        row.add(titleLabel, BorderLayout.NORTH);
        row.add(value, BorderLayout.CENTER);
        return row;
    }

    private JPanel createDetailMetaCard(String title, JLabel valueLabel) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setOpaque(true);
        card.setBackground(AppTheme.withAlpha(AppTheme.getSurfaceBackground(), 215));
        card.setBorder(AppTheme.createRoundedBorder(new Insets(10, 10, 10, 10), AppTheme.withAlpha(AppTheme.getBorderColor(), 145), 1f));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12.5f));
        titleLabel.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 175));

        valueLabel.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 220));
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.PLAIN, 13.25f));
        valueLabel.setVerticalAlignment(SwingConstants.TOP);

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private void styleDetailStatusBadge() {
        detailStatusBadgeLabel.setOpaque(true);
        detailStatusBadgeLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.withAlpha(AppTheme.getBorderColor(), 170), 1, true),
                BorderFactory.createEmptyBorder(7, 11, 7, 11)
        ));
        detailStatusBadgeLabel.setFont(detailStatusBadgeLabel.getFont().deriveFont(Font.BOLD, 11.75f));
        detailStatusBadgeLabel.setBackground(AppTheme.withAlpha(AppTheme.getForeground(), 12));
        detailStatusBadgeLabel.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 210));
    }

    private void updateDetailVisualState(String message) {
        ViewState state = resolveCurrentDetailVisualState();
        detailStatusBadgeLabel.setText(switch (state) {
            case LOADING -> "Cargando";
            case READY -> "Listo";
            case EMPTY -> "Sin contenido";
            case ERROR -> "Error";
            case BLOCKED -> "Revisar";
            case IDLE -> "Sin seleccion";
        });

        Color foreground = switch (state) {
            case READY -> AppTheme.getSuccessColor();
            case ERROR -> AppTheme.getDangerColor();
            case BLOCKED -> AppTheme.getWarningColor();
            case LOADING -> AppTheme.getMainAccent();
            default -> AppTheme.withAlpha(AppTheme.getForeground(), 210);
        };
        Color background = switch (state) {
            case READY -> AppTheme.withAlpha(AppTheme.getSuccessColor(), 34);
            case ERROR -> AppTheme.withAlpha(AppTheme.getDangerColor(), 30);
            case BLOCKED -> AppTheme.withAlpha(AppTheme.getWarningColor(), 28);
            case LOADING -> AppTheme.withAlpha(AppTheme.getMainAccent(), 24);
            default -> AppTheme.withAlpha(AppTheme.getForeground(), 12);
        };
        detailStatusBadgeLabel.setForeground(foreground);
        detailStatusBadgeLabel.setBackground(background);
        detailStatusBadgeLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.withAlpha(foreground, 70), 1, true),
                BorderFactory.createEmptyBorder(7, 11, 7, 11)
        ));

        detailProjectUrlLabel.setText(formatProjectLink(detailProjectUrlLabel.getText()));
        detailProjectUrlLabel.setForeground(isMeaningfulValue(detailProjectUrlLabel.getText())
                ? AppTheme.getLinkForeground()
                : AppTheme.withAlpha(AppTheme.getForeground(), 160));
        detailDescriptionArea.setForeground(state == ViewState.ERROR
                ? AppTheme.withAlpha(AppTheme.getForeground(), 205)
                : AppTheme.getForeground());
        detailDescriptionArea.setText(buildDescriptionWithInstallContext(defaultString(message, "Sin descripcion disponible.")));
    }

    private String buildDescriptionWithInstallContext(String fallbackMessage) {
        String base = isMeaningfulValue(detailBaseDescription)
                ? detailBaseDescription
                : defaultString(fallbackMessage, "Sin descripcion disponible.");
        ExtensionInstallResolution resolution = selectedInstallResolution;
        if (resolution == null || resolution.state() == ExtensionInstallResolutionState.AVAILABLE) {
            return base;
        }
        StringBuilder sb = new StringBuilder(base);
        sb.append("\n\nEstado en este servidor:");
        sb.append("\n- ").append(defaultString(resolution.message(), "Hay una incidencia con esta instalacion."));
        if (resolution.installedVersion() != null && !resolution.installedVersion().isBlank()) {
            sb.append("\n- Version instalada: ").append(resolution.installedVersion());
        }
        if (resolution.requestedVersion() != null && !resolution.requestedVersion().isBlank()) {
            sb.append("\n- Version remota seleccionada: ").append(resolution.requestedVersion());
        }
        if (resolution.targetFileName() != null && !resolution.targetFileName().isBlank()) {
            sb.append("\n- Archivo objetivo: ").append(resolution.targetFileName());
        }
        return sb.toString();
    }

    private ViewState resolveCurrentDetailVisualState() {
        if (selectedEntry == null) {
            return ViewState.IDLE;
        }
        if (detailState.state() == ViewState.LOADING || previewState.state() == ViewState.LOADING) {
            return ViewState.LOADING;
        }
        if (detailState.state() == ViewState.ERROR || previewState.state() == ViewState.ERROR) {
            return ViewState.ERROR;
        }
        if (previewState.state() == ViewState.BLOCKED) {
            return ViewState.BLOCKED;
        }
        if (selectedInstallResolution != null && selectedInstallResolution.blocksInstall()) {
            return ViewState.BLOCKED;
        }
        if (detailState.state() == ViewState.READY || previewState.state() == ViewState.READY) {
            return ViewState.READY;
        }
        return ViewState.IDLE;
    }

    private String formatProjectLink(String url) {
        if (!isMeaningfulValue(url)) {
            return "Sin enlace disponible";
        }
        String normalizedUrl = url.trim();
        normalizedUrl = normalizedUrl.replaceFirst("^https?://", "");
        normalizedUrl = normalizedUrl.replaceFirst("/$", "");
        return trimToLength(normalizedUrl, 48);
    }

    private boolean isMeaningfulValue(String value) {
        return value != null
                && !value.isBlank()
                && !"-".equals(value.trim())
                && !"Sin enlace disponible".equalsIgnoreCase(value.trim());
    }

    private Component createSpacer() {
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        return spacer;
    }

    private ExtensionCatalogQuery buildSearchQuery() {
        PlatformFilterOption platformOption = (PlatformFilterOption) loaderCombo.getSelectedItem();
        boolean compatibleOnly = compatibilityOnlyCheck.isSelected();
        ServerPlatform platform = compatibleOnly && platformOption != null ? platformOption.platform() : ServerPlatform.UNKNOWN;
        String version = compatibleOnly ? normalized(versionField.getText()) : null;
        return new ExtensionCatalogQuery(
                normalized(searchField.getText()),
                platform,
                resolveServerExtensionType(),
                version,
                searchLimit
        );
    }

    private MarketplaceSearchSpec buildSearchSpec() {
        ProviderFilterOption providerOption = (ProviderFilterOption) providerCombo.getSelectedItem();
        return new MarketplaceSearchSpec(
                buildSearchQuery(),
                providerOption == null ? ProviderFilterOption.all() : providerOption,
                compatibilityOnlyCheck.isSelected()
        );
    }

    private ServerExtensionType resolveServerExtensionType() {
        return switch (server == null || server.getEcosystemType() == null ? ServerEcosystemType.UNKNOWN : server.getEcosystemType()) {
            case MODS -> ServerExtensionType.MOD;
            case PLUGINS -> ServerExtensionType.PLUGIN;
            default -> ServerExtensionType.UNKNOWN;
        };
    }

    private boolean matchesProviderFilter(ExtensionCatalogEntry entry, ProviderFilterOption option) {
        if (entry == null || option == null || option.allProviders) {
            return true;
        }
        return option.providerId.equalsIgnoreCase(entry.providerId());
    }

    private MarketplaceCompatibilityAssessment assessEntryCompatibility(ExtensionCatalogEntry entry) {
        if (entry == null) {
            return new MarketplaceCompatibilityAssessment(
                    ExtensionCompatibilityStatus.WARNING,
                    "Compatibilidad sin datos suficientes",
                    List.of("No hay informacion suficiente del proveedor para evaluar esta extension.")
            );
        }
        return assessCompatibility(
                entry.extensionType(),
                entry.compatiblePlatforms(),
                entry.compatibleMinecraftVersions()
        );
    }

    private MarketplaceCompatibilityAssessment assessVersionCompatibility(ExtensionCatalogVersion version) {
        if (version == null) {
            return new MarketplaceCompatibilityAssessment(
                    ExtensionCompatibilityStatus.WARNING,
                    "Version sin datos suficientes",
                    List.of("No hay informacion suficiente para validar esta version.")
            );
        }
        ServerExtensionType type = resolveServerExtensionType();
        return assessCompatibility(type, version.supportedPlatforms(), version.supportedMinecraftVersions());
    }

    private MarketplaceCompatibilityAssessment assessCompatibility(ServerExtensionType extensionType,
                                                                   java.util.Set<ServerPlatform> declaredPlatforms,
                                                                   java.util.Set<String> declaredVersions) {
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        ServerEcosystemType serverEcosystem = server == null || server.getEcosystemType() == null
                ? ServerEcosystemType.UNKNOWN
                : server.getEcosystemType();
        ServerPlatform serverPlatform = server == null || server.getPlatform() == null
                ? ServerPlatform.UNKNOWN
                : canonicalizePlatform(server.getPlatform());
        String serverVersion = normalized(server == null ? null : server.getVersion());

        if (serverEcosystem == ServerEcosystemType.MODS && extensionType == ServerExtensionType.PLUGIN) {
            blockers.add("Esta extension es un plugin y el servidor actual usa mods.");
        } else if (serverEcosystem == ServerEcosystemType.PLUGINS && extensionType == ServerExtensionType.MOD) {
            blockers.add("Esta extension es un mod y el servidor actual usa plugins.");
        } else if (extensionType == null || extensionType == ServerExtensionType.UNKNOWN) {
            warnings.add("El proveedor no deja claro si es un mod o un plugin.");
        }

        if (serverPlatform == ServerPlatform.UNKNOWN) {
            warnings.add("No se conoce la plataforma exacta del servidor.");
        } else if (declaredPlatforms == null || declaredPlatforms.isEmpty()) {
            warnings.add("El proveedor no declara loaders o plataformas compatibles.");
        } else {
            boolean platformMatch = declaredPlatforms.stream()
                    .filter(platform -> platform != null && platform != ServerPlatform.UNKNOWN)
                    .map(this::canonicalizePlatform)
                    .anyMatch(platform -> platform == serverPlatform);
            if (!platformMatch) {
                blockers.add("La extension declara compatibilidad con " + joinPreview(
                        declaredPlatforms.stream().map(this::labelForPlatform).toList(), 4
                ) + ", pero el servidor actual usa " + labelForPlatform(serverPlatform) + ".");
            }
        }

        if (serverVersion == null) {
            warnings.add("No se conoce la version de Minecraft del servidor.");
        } else if (declaredVersions == null || declaredVersions.isEmpty()) {
            warnings.add("El proveedor no declara versiones de Minecraft compatibles.");
        } else {
            VersionMatchType versionMatch = matchMinecraftVersion(serverVersion, declaredVersions);
            if (versionMatch == VersionMatchType.NONE) {
                blockers.add("La extension no declara compatibilidad para Minecraft " + serverVersion + ".");
            } else if (versionMatch == VersionMatchType.FAMILY) {
                warnings.add("La compatibilidad solo coincide por familia de version (" + serverVersionFamily(serverVersion) + ").");
            }
        }

        if (!blockers.isEmpty()) {
            return new MarketplaceCompatibilityAssessment(
                    ExtensionCompatibilityStatus.INCOMPATIBLE,
                    blockers.getFirst(),
                    List.copyOf(blockers)
            );
        }
        if (!warnings.isEmpty()) {
            return new MarketplaceCompatibilityAssessment(
                    ExtensionCompatibilityStatus.WARNING,
                    warnings.getFirst(),
                    List.copyOf(warnings)
            );
        }
        return new MarketplaceCompatibilityAssessment(
                ExtensionCompatibilityStatus.COMPATIBLE,
                "Compatible con este servidor.",
                List.of("Plataforma, tipo de extension y version de Minecraft coinciden con el servidor actual.")
        );
    }

    private String describeServerBaseline() {
        String platform = server == null || server.getPlatform() == null ? "?" : labelForPlatform(server.getPlatform());
        String version = normalized(server == null ? null : server.getVersion());
        return "Servidor: " + platform + "  |  Minecraft " + (version == null ? "sin version" : version);
    }

    private boolean providerAllowedForServer(ExtensionCatalogProviderDescriptor provider) {
        if (provider == null || provider.providerId() == null) {
            return false;
        }
        ServerEcosystemType ecosystem = server == null || server.getEcosystemType() == null
                ? ServerEcosystemType.UNKNOWN
                : server.getEcosystemType();
        String providerId = provider.providerId().trim().toLowerCase(Locale.ROOT);
        if (ecosystem == ServerEcosystemType.MODS) {
            return !"hangar".equals(providerId);
        }
        return true;
    }

    private String describeProvider(String providerId) {
        String resolved = providerLabelsById.get(normalized(providerId));
        if (resolved != null && !resolved.isBlank()) {
            return resolved;
        }
        return providerId == null ? "-" : providerId;
    }

    private String buildDetailsDescription(ExtensionCatalogEntry entry, ExtensionCatalogDetails details) {
        StringBuilder sb = new StringBuilder();
        if (details != null && details.summary() != null && !details.summary().isBlank()) {
            sb.append(cleanDisplayText(details.summary()));
        } else if (entry.description() != null && !entry.description().isBlank()) {
            sb.append(cleanDisplayText(entry.description()));
        } else {
            sb.append("Sin descripcion disponible.");
        }

        if (details != null && details.categories() != null && !details.categories().isEmpty()) {
            sb.append("\n\nCategorias: ").append(String.join(", ", details.categories()));
        }
        if (entry.compatibleMinecraftVersions() != null && !entry.compatibleMinecraftVersions().isEmpty()) {
            sb.append("\nCompatibilidad declarada: ").append(String.join(", ", entry.compatibleMinecraftVersions()));
        }
        MarketplaceCompatibilityAssessment assessment = assessEntryCompatibility(entry);
        if (assessment.reasons() != null && !assessment.reasons().isEmpty()) {
            sb.append("\n\nCompatibilidad con este servidor:");
            for (String reason : assessment.reasons()) {
                sb.append("\n- ").append(reason);
            }
        }
        return sb.toString();
    }

    private String buildAvailableVersionsPreview(List<ExtensionCatalogVersion> versions) {
        if (versions == null || versions.isEmpty()) {
            return "Sin versiones declaradas";
        }
        List<String> compatible = versions.stream()
                .filter(version -> assessVersionCompatibility(version).status() == ExtensionCompatibilityStatus.COMPATIBLE)
                .map(version -> defaultString(version.versionNumber(), version.displayName()))
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .limit(3)
                .toList();
        if (!compatible.isEmpty()) {
            return "Compatible: " + String.join(", ", compatible);
        }
        return "Sin build compatible marcada";
    }

    private String describeVersionMeta(ExtensionCatalogVersion version) {
        StringBuilder sb = new StringBuilder();
        if (version.publishedAtEpochMillis() > 0L) {
            sb.append(VERSION_DATE_FORMAT.format(Instant.ofEpochMilli(version.publishedAtEpochMillis())));
        }
        if (version.supportedMinecraftVersions() != null && !version.supportedMinecraftVersions().isEmpty()) {
            if (sb.length() > 0) {
                sb.append("  |  ");
            }
            sb.append(String.join(", ", version.supportedMinecraftVersions()));
        }
        return sb.length() == 0 ? "Sin metadata adicional" : sb.toString();
    }

    private void restartDebounce() {
        debounceTimer.restart();
    }

    private int compatibilityRank(ExtensionCompatibilityStatus status) {
        return switch (status == null ? ExtensionCompatibilityStatus.WARNING : status) {
            case COMPATIBLE -> 0;
            case WARNING -> 1;
            case INCOMPATIBLE -> 2;
        };
    }

    private VersionMatchType matchMinecraftVersion(String serverVersion, java.util.Set<String> declaredVersions) {
        if (serverVersion == null || declaredVersions == null || declaredVersions.isEmpty()) {
            return VersionMatchType.NONE;
        }
        String normalizedServerVersion = serverVersion.trim();
        String serverFamily = serverVersionFamily(normalizedServerVersion);
        boolean familyMatch = false;
        for (String declared : declaredVersions) {
            if (declared == null || declared.isBlank()) {
                continue;
            }
            String normalizedDeclared = declared.trim();
            if (normalizedDeclared.equalsIgnoreCase(normalizedServerVersion)) {
                return VersionMatchType.EXACT;
            }
            if (serverFamily.equals(serverVersionFamily(normalizedDeclared))) {
                familyMatch = true;
            }
        }
        return familyMatch ? VersionMatchType.FAMILY : VersionMatchType.NONE;
    }

    private String serverVersionFamily(String version) {
        if (version == null || version.isBlank()) {
            return "";
        }
        String[] segments = version.trim().split("\\.");
        if (segments.length < 2) {
            return version.trim();
        }
        return segments[0] + "." + segments[1];
    }

    private String labelForPlatform(ServerPlatform platform) {
        if (platform == null) {
            return "Desconocido";
        }
        return switch (platform) {
            case NEOFORGE -> "NeoForge";
            case PUFFERFISH -> "Pufferfish";
            default -> {
                String lower = platform.name().toLowerCase(Locale.ROOT);
                yield Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
            }
        };
    }

    private ServerPlatform canonicalizePlatform(ServerPlatform platform) {
        if (platform == null) {
            return ServerPlatform.UNKNOWN;
        }
        return switch (platform) {
            case PURPUR, PUFFERFISH -> ServerPlatform.PAPER;
            default -> platform;
        };
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String appendRecoveryNote(String base, String note) {
        if (note == null || note.isBlank()) {
            return base;
        }
        String normalizedBase = defaultString(base, "");
        if (normalizedBase.contains(note)) {
            return normalizedBase;
        }
        if (normalizedBase.isBlank()) {
            return note;
        }
        return normalizedBase + "\n\n" + note;
    }

    private String buildDetailSubtitle(String author, String providerId) {
        String authorText = defaultString(author, "Autor desconocido");
        String providerText = defaultString(describeProvider(providerId), "Proveedor no disponible");
        return authorText + "  |  " + providerText;
    }

    private String normalized(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
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

    private String friendlySearchError(Throwable error) {
        String root = rootMessage(error).toLowerCase(Locale.ROOT);
        if (root.contains("ningun proveedor") || root.contains("http") || root.contains("consult")) {
            return "No se ha podido consultar el marketplace ahora mismo.";
        }
        if (root.contains("interrump")) {
            return "La busqueda se ha interrumpido antes de terminar.";
        }
        return "No se ha podido actualizar la busqueda del marketplace.";
    }

    private String friendlyDetailError(Throwable error) {
        String root = rootMessage(error).toLowerCase(Locale.ROOT);
        if (root.contains("http") || root.contains("consult")) {
            return "No se ha podido cargar el detalle remoto en este momento.";
        }
        return "No se ha podido ampliar la informacion de esta extension.";
    }

    private String friendlyDetailRecoveryHint(Throwable error) {
        String root = rootMessage(error).toLowerCase(Locale.ROOT);
        if (root.contains("http") || root.contains("consult")) {
            return "Puedes seguir revisando la ficha resumida y reintentar la carga volviendo a seleccionar la extension o refrescando la busqueda.";
        }
        return "Puedes seguir usando la informacion del listado y reintentar la carga de detalles mas tarde.";
    }

    private String friendlyPreviewError(Throwable error) {
        String root = rootMessage(error).toLowerCase(Locale.ROOT);
        if (root.contains("http") || root.contains("consult")) {
            return "No se ha podido preparar la descarga desde el proveedor.";
        }
        if (root.contains("interrump")) {
            return "La preparacion de la descarga se ha interrumpido.";
        }
        return "No se ha podido preparar la instalacion de forma segura.";
    }

    private String friendlyPreviewRecoveryHint(Throwable error) {
        String root = rootMessage(error).toLowerCase(Locale.ROOT);
        if (root.contains("compatible")) {
            return "Prueba otra version o desactiva el filtro de solo compatibles para revisar mas builds declaradas.";
        }
        if (root.contains("http") || root.contains("consult")) {
            return "La instalacion queda bloqueada hasta poder contactar otra vez con el proveedor remoto.";
        }
        return "La accion queda bloqueada para evitar una instalacion incompleta o incoherente.";
    }

    private String friendlySyncError(Throwable error) {
        String root = rootMessage(error).toLowerCase(Locale.ROOT);
        if (root.contains("http") || root.contains("consult")) {
            return "No se ha podido actualizar el estado remoto de las extensiones instaladas.";
        }
        if (root.contains("ningun proveedor")) {
            return "No se ha podido consultar ningun proveedor para sincronizar la biblioteca.";
        }
        return "No se ha podido sincronizar la biblioteca instalada del servidor.";
    }

    private String friendlySyncRecoveryHint(Throwable error) {
        String root = rootMessage(error).toLowerCase(Locale.ROOT);
        if (root.contains("http") || root.contains("consult") || root.contains("ningun proveedor")) {
            return "Se conserva la informacion local actual. Puedes reintentar la sincronizacion mas tarde.";
        }
        return "La biblioteca local se mantiene como estaba antes del intento de sincronizacion.";
    }

    private final class SearchResultRenderer extends JPanel implements ListCellRenderer<MarketplaceEntryViewModel> {
        private final DefaultListCellRenderer fallback = new DefaultListCellRenderer();
        private final JPanel iconWell = new JPanel(new BorderLayout());
        private final JLabel iconLabel = new JLabel();
        private final JPanel titleRow = new JPanel(new BorderLayout(8, 0));
        private final JLabel nameLabel = new JLabel();
        private final JLabel metaLabel = new JLabel();
        private final JLabel descriptionLabel = new JLabel();
        private final JPanel badgesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));

        private SearchResultRenderer() {
            super(new BorderLayout(8, 0));
            setOpaque(true);

            JPanel iconPanel = new JPanel(new BorderLayout());
            iconPanel.setOpaque(false);
            iconPanel.setPreferredSize(new Dimension(42, 42));
            iconWell.setOpaque(true);
            iconWell.setBorder(AppTheme.createRoundedBorder(new Insets(4, 4, 4, 4), AppTheme.withAlpha(AppTheme.getBorderColor(), 140), 1f));
            iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
            iconLabel.setVerticalAlignment(SwingConstants.CENTER);
            iconWell.add(iconLabel, BorderLayout.CENTER);
            iconPanel.add(iconWell, BorderLayout.NORTH);
            add(iconPanel, BorderLayout.WEST);

            JPanel textPanel = new JPanel();
            textPanel.setOpaque(false);
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 15.5f));
            metaLabel.setFont(metaLabel.getFont().deriveFont(Font.PLAIN, 12.5f));
            metaLabel.setForeground(AppTheme.getMutedForeground());
            metaLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(13.25f));
            descriptionLabel.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 210));
            descriptionLabel.setVerticalAlignment(SwingConstants.TOP);

            badgesPanel.setOpaque(false);
            titleRow.setOpaque(false);
            titleRow.add(nameLabel, BorderLayout.CENTER);
            titleRow.add(metaLabel, BorderLayout.EAST);

            textPanel.add(titleRow);
            textPanel.add(Box.createVerticalStrut(6));
            textPanel.add(descriptionLabel);
            textPanel.add(Box.createVerticalStrut(6));
            textPanel.add(badgesPanel);
            add(textPanel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends MarketplaceEntryViewModel> list,
                                                      MarketplaceEntryViewModel value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            if (value == null || value.entry() == null) {
                return fallback.getListCellRendererComponent(list, "", index, isSelected, cellHasFocus);
            }

            ExtensionCatalogEntry entry = value.entry();
            setBackground(isSelected ? AppTheme.getSoftSelectionBackground() : AppTheme.getPanelBackground());
            setBorder(AppTheme.createRoundedBorder(
                    new Insets(8, 8, 8, 8),
                    isSelected ? AppTheme.getMainAccent() : AppTheme.getBorderColor(),
                    1f
            ));
            iconWell.setBackground(isSelected
                    ? AppTheme.withAlpha(AppTheme.getMainAccent(), 18)
                    : AppTheme.withAlpha(AppTheme.getForeground(), 12));
            iconLabel.setIcon(ExtensionIconLoader.getIcon(entry.iconUrl(), 32, list::repaint));
            nameLabel.setText(defaultString(entry.displayName(), "Extension"));
            metaLabel.setText(defaultString(entry.author(), "Autor desconocido") + "  |  " + defaultString(value.providerLabel(), describeProvider(entry.providerId())));
            int textWidth = Math.max(320, list.getWidth() - 122);
            descriptionLabel.setText("<html><body style='width:" + textWidth + "px'>" + escapeHtml(defaultString(value.descriptionPreview(), "Sin descripcion.")) + "</body></html>");

            badgesPanel.removeAll();
            appendResultBadges(badgesPanel, value);
            return this;
        }
    }

    private final class VersionRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list,
                                                      Object value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            VersionOption option = (VersionOption) value;
            String text = option == null ? "-" : option.displayName + "  |  " + option.meta;
            JLabel label = (JLabel) super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
            if (option != null && option.compatible()) {
                label.setFont(label.getFont().deriveFont(Font.BOLD));
            }
            return label;
        }
    }

    private final class QueueItemRenderer implements ListCellRenderer<DownloadQueueItem> {
        @Override
        public Component getListCellRendererComponent(JList<? extends DownloadQueueItem> list,
                                                      DownloadQueueItem value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            JPanel card = new JPanel(new BorderLayout(10, 0));
            card.setOpaque(true);
            card.setBackground(isSelected ? AppTheme.getSoftSelectionBackground() : AppTheme.getPanelBackground());
            card.setBorder(AppTheme.createRoundedBorder(
                    new Insets(8, 8, 8, 8),
                    isSelected ? AppTheme.getMainAccent() : AppTheme.getBorderColor(),
                    1f
            ));

            JLabel state = new JLabel(symbolFor(value == null ? QueueState.PENDING : value.state), SwingConstants.CENTER);
            state.setOpaque(true);
            state.setPreferredSize(new Dimension(48, 48));
            state.setForeground(Color.WHITE);
            state.setBackground(colorFor(value == null ? QueueState.PENDING : value.state));
            card.add(state, BorderLayout.WEST);

            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            JLabel title = new JLabel(value == null ? "Descarga" : value.displayName);
            title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
            JLabel meta = new JLabel(value == null ? "-" : queueStateLabel(value.state) + "  |  " + value.versionLabel);
            meta.setFont(meta.getFont().deriveFont(12.5f));
            meta.setForeground(AppTheme.getMutedForeground());
            JLabel message = new JLabel(value == null ? "-" : "<html><body style='width:420px'>" + escapeHtml(value.message) + "</body></html>");
            message.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 195));
            text.add(title);
            text.add(Box.createVerticalStrut(3));
            text.add(meta);
            text.add(Box.createVerticalStrut(2));
            text.add(message);
            card.add(text, BorderLayout.CENTER);
            return card;
        }

        private String symbolFor(QueueState state) {
            return switch (state) {
                case PENDING -> "EN";
                case DOWNLOADING -> "DL";
                case COMPLETED -> "OK";
                case FAILED -> "!";
            };
        }

        private String queueStateLabel(QueueState state) {
            return switch (state) {
                case PENDING -> "En cola";
                case DOWNLOADING -> "Instalando";
                case COMPLETED -> "Completada";
                case FAILED -> "Con incidencia";
            };
        }

        private Color colorFor(QueueState state) {
            return switch (state) {
                case PENDING -> AppTheme.getMainAccent();
                case DOWNLOADING -> AppTheme.tint(AppTheme.getMainAccent(), Color.WHITE, 0.18f);
                case COMPLETED -> AppTheme.getSuccessColor();
                case FAILED -> AppTheme.getDangerColor();
            };
        }
    }

    private JLabel createBadge(String text, Color background, Color foreground) {
        JLabel label = new JLabel(text);
        label.setOpaque(true);
        label.setBackground(background);
        label.setForeground(foreground);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11.5f));
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.withAlpha(foreground, 42), 1, true),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        return label;
    }

    private void appendResultBadges(JPanel badges, MarketplaceEntryViewModel viewModel) {
        if (badges == null || viewModel == null || viewModel.entry() == null) {
            return;
        }
        if (viewModel.queueStateText() != null) {
            badges.add(createBadge(
                    viewModel.queueStateText(),
                    AppTheme.withAlpha(AppTheme.getWarningColor(), 26),
                    AppTheme.getWarningColor()
            ));
        }
        badges.add(createBadge(
                compatibilityBadgeText(new MarketplaceCompatibilityAssessment(
                        viewModel.compatibilityStatus(),
                        viewModel.compatibilitySummary(),
                        viewModel.compatibilityReasons()
                )),
                compatibilityBadgeBackground(viewModel.compatibilityStatus()),
                compatibilityBadgeForeground(viewModel.compatibilityStatus())
        ));
    }

    private String escapeHtml(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String compatibilityBadgeText(MarketplaceCompatibilityAssessment assessment) {
        if (assessment == null || assessment.status() == null) {
            return "Compatibilidad sin validar";
        }
        return switch (assessment.status()) {
            case COMPATIBLE -> "Compatible";
            case WARNING -> "Revisar";
            case INCOMPATIBLE -> "No compatible";
        };
    }

    private String buildCompatibilityDisplayText(MarketplaceCompatibilityAssessment assessment) {
        if (assessment == null) {
            return "Compatibilidad sin validar";
        }
        if (assessment.reasons() == null || assessment.reasons().isEmpty()) {
            return assessment.summary();
        }
        return assessment.summary() + "  |  " + trimToLength(assessment.reasons().getFirst(), 72);
    }

    private Color compatibilityBadgeForeground(ExtensionCompatibilityStatus status) {
        return switch (status == null ? ExtensionCompatibilityStatus.WARNING : status) {
            case COMPATIBLE -> AppTheme.withAlpha(AppTheme.getForeground(), 215);
            case WARNING -> AppTheme.getWarningColor();
            case INCOMPATIBLE -> AppTheme.getDangerColor();
        };
    }

    private Color compatibilityBadgeBackground(ExtensionCompatibilityStatus status) {
        return switch (status == null ? ExtensionCompatibilityStatus.WARNING : status) {
            case COMPATIBLE -> AppTheme.withAlpha(AppTheme.getMainAccent(), 22);
            case WARNING -> AppTheme.withAlpha(AppTheme.getWarningColor(), 24);
            case INCOMPATIBLE -> AppTheme.withAlpha(AppTheme.getDangerColor(), 26);
        };
    }

    private String summarizePlatforms(ExtensionCatalogEntry entry) {
        if (entry == null || entry.compatiblePlatforms() == null || entry.compatiblePlatforms().isEmpty()) {
            return "Loader: cualquiera";
        }
        List<String> labels = entry.compatiblePlatforms().stream()
                .map(this::labelForPlatform)
                .filter(label -> label != null && !label.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        return "Loader: " + joinPreview(labels, 3);
    }

    private String summarizeVersions(ExtensionCatalogEntry entry) {
        if (entry == null || entry.compatibleMinecraftVersions() == null || entry.compatibleMinecraftVersions().isEmpty()) {
            return "MC: sin declarar";
        }
        return "MC: " + joinPreview(entry.compatibleMinecraftVersions(), 4);
    }

    private String joinPreview(Iterable<String> values, int limit) {
        if (values == null) {
            return "-";
        }
        List<String> cleaned = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String trimmed = value.trim();
            if (!cleaned.contains(trimmed)) {
                cleaned.add(trimmed);
            }
        }
        if (cleaned.isEmpty()) {
            return "-";
        }
        if (cleaned.size() <= limit) {
            return String.join(", ", cleaned);
        }
        return String.join(", ", cleaned.subList(0, limit)) + " +" + (cleaned.size() - limit);
    }

    private String normalizeInlineText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return cleanDisplayText(text).replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String cleanDisplayText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String cleaned = text
                .replaceAll("(?i)<\\s*br\\s*/?\\s*>", "\n")
                .replaceAll("(?i)</\\s*p\\s*>", "\n\n")
                .replaceAll("(?i)</\\s*li\\s*>", "\n")
                .replaceAll("(?i)<\\s*li[^>]*>", "- ")
                .replaceAll("<[^>]+>", "");
        return decodeBasicHtmlEntities(cleaned)
                .replace('\u00a0', ' ')
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String decodeBasicHtmlEntities(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
    }

    private String trimToLength(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

    private enum ViewState {
        IDLE,
        LOADING,
        READY,
        EMPTY,
        ERROR,
        BLOCKED
    }

    private record SearchViewState(ViewState state, String message, long requestId) {
    }

    private record MarketplaceSearchSpec(ExtensionCatalogQuery query,
                                         ProviderFilterOption provider,
                                         boolean compatibilityOnly) {
    }

    private record DetailViewState(ViewState state,
                                   String message,
                                   ExtensionCatalogEntry entry,
                                   long requestId) {
    }

    private record PreviewViewState(ViewState state,
                                    String message,
                                    String versionId,
                                    long requestId) {
    }

    private record QueueViewState(ViewState state, String message) {
    }

    private record IconViewState(ViewState state, String message) {
    }

    private enum VersionMatchType {
        EXACT,
        FAMILY,
        NONE
    }

    private record MarketplaceCompatibilityAssessment(
            ExtensionCompatibilityStatus status,
            String summary,
            List<String> reasons
    ) {
    }

    private record ProviderFilterOption(String providerId, String displayName, boolean allProviders) {
        static ProviderFilterOption all() {
            return new ProviderFilterOption("*", "Todos los proveedores", true);
        }

        static ProviderFilterOption provider(String providerId, String displayName) {
            return new ProviderFilterOption(providerId, displayName, false);
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private record PlatformFilterOption(String label, ServerPlatform platform, boolean preferred) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record VersionOption(String versionId, String displayName, String meta, boolean compatible) {
        @Override
        public String toString() {
            return displayName;
        }
    }

    private enum QueueState {
        PENDING,
        DOWNLOADING,
        COMPLETED,
        FAILED
    }

    private static final class DownloadQueueItem {
        private final String providerId;
        private final String projectId;
        private final String versionId;
        private final String displayName;
        private final String versionLabel;
        private QueueState state;
        private String message;

        private DownloadQueueItem(String providerId,
                                  String projectId,
                                  String versionId,
                                  String displayName,
                                  String versionLabel,
                                  QueueState state,
                                  String message) {
            this.providerId = providerId;
            this.projectId = projectId;
            this.versionId = versionId;
            this.displayName = displayName;
            this.versionLabel = versionLabel;
            this.state = state;
            this.message = message;
        }

        private boolean matchesProject(String providerId, String projectId) {
            return this.providerId != null
                    && this.projectId != null
                    && providerId != null
                    && projectId != null
                    && this.providerId.equalsIgnoreCase(providerId)
                    && this.projectId.equalsIgnoreCase(projectId);
        }

        private boolean matchesExactVersion(String providerId, String projectId, String versionId) {
            return matchesProject(providerId, projectId)
                    && this.versionId != null
                    && versionId != null
                    && this.versionId.equalsIgnoreCase(versionId);
        }
    }

    private record QueueAdmission(boolean allowed, DownloadQueueItem existingItem, String message) {
    }

    private record ActionAvailability(boolean canInstallNow, boolean canQueue, String detailMessage) {
    }

    @FunctionalInterface
    private interface DocumentChangeHandler {
        void onChange();
    }

    private static final class SimpleDocumentListener implements DocumentListener {
        private final DocumentChangeHandler handler;

        private SimpleDocumentListener(DocumentChangeHandler handler) {
            this.handler = handler;
        }

        static SimpleDocumentListener of(DocumentChangeHandler handler) {
            return new SimpleDocumentListener(handler);
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            handler.onChange();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            handler.onChange();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            handler.onChange();
        }
    }
}
