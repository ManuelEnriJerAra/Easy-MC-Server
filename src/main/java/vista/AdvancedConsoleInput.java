package vista;

import controlador.console.ConsoleCommandContext;
import controlador.console.ConsoleCommandContextFactory;
import controlador.console.ConsoleRuntimeContextSynchronizer;
import controlador.console.Suggestion;
import controlador.console.SuggestionCategory;
import controlador.console.SuggestionEngine;
import controlador.console.SuggestionEngineResult;
import controlador.console.TabCompletionPlan;
import controlador.console.providers.SuggestionProviders;
import controlador.console.vanilla.VanillaCatalogService;
import modelo.Server;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.HierarchyEvent;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Campo de consola avanzado con popup de sugerencias y navegacion por teclado.
 */
public final class AdvancedConsoleInput extends JPanel {
    private static final int MAX_VISIBLE_ROWS = 7;
    private static final int QUERY_DELAY_MS = 90;
    private static final int POPUP_GAP_PX = 12;
    private static final int LOADING_POLL_MS = 700;
    private static final int LOADING_ROTATION_TIMER_MS = 16;
    private static final double LOADING_ROTATION_STEP_RADIANS = -Math.toRadians(6);

    private final JTextField inputField = new JTextField();
    private final JPanel loadingPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    private final JLabel loadingIconLabel = new JLabel();
    private final JLabel loadingTextLabel = new JLabel("Cargando ayudas de terminal");
    private final DefaultListModel<Suggestion> suggestionModel = new DefaultListModel<>();
    private final JList<Suggestion> suggestionList = new JList<>(suggestionModel);
    private final JPopupMenu suggestionPopup = new JPopupMenu();
    private final JScrollPane suggestionScrollPane;
    private final SuggestionEngine suggestionEngine = new SuggestionEngine(SuggestionProviders.defaultHybridService());
    private final VanillaCatalogService vanillaCatalogService = new VanillaCatalogService();
    private final Executor executor = SuggestionProviders.defaultExecutor();
    private final ConsoleCommandContextFactory contextFactory = new ConsoleCommandContextFactory();
    private final ConsoleRuntimeContextSynchronizer contextSynchronizer = new ConsoleRuntimeContextSynchronizer(contextFactory);
    private final Supplier<Server> serverSupplier;
    private final Supplier<Set<String>> onlinePlayersSupplier;
    private final Consumer<String> commandSender;
    private final AtomicInteger requestSequence = new AtomicInteger();
    private final Timer queryTimer;
    private final Timer loadingPollTimer;
    private final Timer loadingRotationTimer;
    private final SvgIconFactory.RotatingIcon loadingIcon;

    private volatile SuggestionEngineResult lastResult;
    private ConsoleCommandContext currentContext = ConsoleCommandContext.empty();
    private String offlinePlaceholder = "Inicia el servidor para usar la consola";
    private boolean terminalHelpLoading;
    private boolean terminalHelpLoadingRequested;

    public AdvancedConsoleInput(
            Supplier<Server> serverSupplier,
            Supplier<Set<String>> onlinePlayersSupplier,
            Consumer<String> commandSender
    ) {
        super(new BorderLayout());
        this.serverSupplier = Objects.requireNonNull(serverSupplier, "serverSupplier");
        this.onlinePlayersSupplier = Objects.requireNonNull(onlinePlayersSupplier, "onlinePlayersSupplier");
        this.commandSender = Objects.requireNonNull(commandSender, "commandSender");

        setOpaque(false);

        inputField.setBorder(null);
        inputField.setOpaque(false);
        inputField.setForeground(AppTheme.getConsoleForeground());
        inputField.setCaretColor(AppTheme.getConsoleForeground());
        inputField.setSelectionColor(AppTheme.withAlpha(AppTheme.getSelectionBackground(), 180));
        inputField.setSelectedTextColor(AppTheme.getConsoleForeground());
        inputField.setFont(new Font("Monospaced", Font.PLAIN, 12));
        inputField.setFocusTraversalKeysEnabled(false);
        add(inputField, BorderLayout.CENTER);

        loadingIcon = SvgIconFactory.createRotating(
                "easymcicons/refresh.svg",
                14,
                14,
                () -> AppTheme.withAlpha(AppTheme.getConsoleForeground(), 105)
        );
        loadingIconLabel.setIcon(loadingIcon);
        loadingIconLabel.setOpaque(false);
        loadingTextLabel.setOpaque(false);
        loadingPanel.setOpaque(false);
        loadingPanel.add(loadingIconLabel);
        loadingPanel.add(loadingTextLabel);
        loadingPanel.setVisible(false);
        add(loadingPanel, BorderLayout.EAST);

        suggestionList.setModel(suggestionModel);
        suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        suggestionList.setFocusable(false);
        suggestionList.setVisibleRowCount(MAX_VISIBLE_ROWS);
        suggestionList.setCellRenderer(new SuggestionCellRenderer());
        suggestionList.setBackground(AppTheme.getConsoleBackground());
        suggestionList.setFixedCellHeight(-1);

        suggestionScrollPane = new JScrollPane(suggestionList);
        suggestionScrollPane.setBorder(BorderFactory.createEmptyBorder());
        suggestionScrollPane.getViewport().setBackground(AppTheme.getConsoleBackground());
        suggestionScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        suggestionScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        suggestionScrollPane.setWheelScrollingEnabled(true);

        suggestionPopup.setBorder(AppTheme.createRoundedBorder(new Insets(1, 1, 1, 1), AppTheme.getConsoleOutlineColor(), 1f));
        suggestionPopup.setFocusable(false);
        suggestionPopup.add(suggestionScrollPane);

        queryTimer = new Timer(QUERY_DELAY_MS, e -> triggerSuggestionQuery());
        queryTimer.setRepeats(false);
        loadingPollTimer = new Timer(LOADING_POLL_MS, e -> {
            if (!terminalHelpLoading || !currentContext.serverOnline()) {
                return;
            }
            if (!isTerminalHelpWarmupPending()) {
                updateTerminalHelpLoading(false);
                if (inputField.isFocusOwner()) {
                    scheduleSuggestionRefresh();
                }
                return;
            }
            if (inputField.isFocusOwner()) {
                scheduleSuggestionRefresh();
            }
        });
        loadingPollTimer.setRepeats(true);
        loadingRotationTimer = new Timer(LOADING_ROTATION_TIMER_MS, e -> {
            double nextAngle = currentLoadingAngle() + LOADING_ROTATION_STEP_RADIANS;
            loadingIcon.setAngleRadians(nextAngle);
            storeLoadingAngle(nextAngle);
            loadingIconLabel.repaint();
        });
        loadingRotationTimer.setRepeats(true);
        contextSynchronizer.setInvalidationListener(this::handleRuntimeInvalidation);

        installDocumentListener();
        installKeyBindings();
        installFocusHandling();
        installLifecycleCleanup();
        refreshTheme();
        refreshInteractiveState();
    }

    public JTextField getInputField() {
        return inputField;
    }

    public void refreshContext() {
        currentContext = contextSynchronizer.getCurrentContext(serverSupplier.get(), onlinePlayersSupplier.get());
        warmupCommandCatalogIfNeeded();
        refreshInteractiveState();
        if (inputField.isFocusOwner()) {
            scheduleSuggestionRefresh();
        }
    }

    public void notifyOnlinePlayersChanged(Set<String> onlinePlayers) {
        contextSynchronizer.notifyOnlinePlayersChanged(onlinePlayers);
    }

    public void notifyRuntimeStateChanged() {
        contextSynchronizer.notifyRuntimeStateChanged(serverSupplier.get());
        refreshContext();
    }

    public void notifyServerSelectionChanged() {
        contextSynchronizer.notifyServerSelectionChanged(serverSupplier.get());
        refreshContext();
    }

    public void refreshTheme() {
        Color consoleBackground = AppTheme.getConsoleBackground();
        inputField.setForeground(currentContext.serverOnline()
                ? AppTheme.getConsoleForeground()
                : AppTheme.withAlpha(AppTheme.getConsoleForeground(), 150));
        inputField.setCaretColor(AppTheme.getConsoleForeground());
        inputField.setSelectionColor(AppTheme.withAlpha(AppTheme.getSelectionBackground(), 180));
        suggestionList.setBackground(consoleBackground);
        suggestionList.repaint();
        suggestionScrollPane.getViewport().setBackground(consoleBackground);
        suggestionPopup.setBorder(AppTheme.createRoundedBorder(
                new Insets(1, 1, 1, 1),
                AppTheme.withAlpha(AppTheme.getMainAccent(), 150),
                1f
        ));
        loadingTextLabel.setForeground(AppTheme.withAlpha(AppTheme.getConsoleForeground(), 105));
        loadingTextLabel.setFont(inputField.getFont().deriveFont(Font.PLAIN, 11f));
        loadingPanel.repaint();
    }

    public void clearSuggestions() {
        suggestionModel.clear();
        lastResult = null;
        if (suggestionPopup.isVisible()) {
            suggestionPopup.setVisible(false);
        }
    }

    private void installDocumentListener() {
        inputField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                scheduleSuggestionRefresh();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                scheduleSuggestionRefresh();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                scheduleSuggestionRefresh();
            }
        });
    }

    private void installKeyBindings() {
        InputMap inputMap = inputField.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = inputField.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke("ENTER"), "advanced-console-send");
        actionMap.put("advanced-console-send", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                sendCurrentCommand();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("TAB"), "advanced-console-tab");
        actionMap.put("advanced-console-tab", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                applyTabCompletion();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("UP"), "advanced-console-up");
        actionMap.put("advanced-console-up", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                moveSelection(-1);
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("DOWN"), "advanced-console-down");
        actionMap.put("advanced-console-down", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                moveSelection(1);
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("ESCAPE"), "advanced-console-escape");
        actionMap.put("advanced-console-escape", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                clearSuggestions();
            }
        });
    }

    private void installFocusHandling() {
        inputField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                refreshContext();
                scheduleSuggestionRefresh();
            }

            @Override
            public void focusLost(FocusEvent e) {
                SwingUtilities.invokeLater(() -> {
                    if (!inputField.isFocusOwner()) {
                        clearSuggestions();
                    }
                });
            }
        });
    }

    private void installLifecycleCleanup() {
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) == 0) {
                return;
            }
            if (!isDisplayable()) {
                queryTimer.stop();
                loadingPollTimer.stop();
                loadingRotationTimer.stop();
                clearSuggestions();
                contextSynchronizer.close();
            }
        });
    }

    private void scheduleSuggestionRefresh() {
        if (!inputField.isFocusOwner() || !currentContext.serverOnline()) {
            return;
        }
        queryTimer.restart();
    }

    private void triggerSuggestionQuery() {
        Server server = serverSupplier.get();
        if (server == null || !currentContext.serverOnline()) {
            updateTerminalHelpLoading(false);
            clearSuggestions();
            return;
        }

        currentContext = contextSynchronizer.getCurrentContext(server, onlinePlayersSupplier.get());
        warmupCommandCatalogIfNeeded();
        int requestId = requestSequence.incrementAndGet();
        String text = inputField.getText();
        int caret = inputField.getCaretPosition();

        suggestionEngine.suggest(text, caret, currentContext, executor)
                .whenComplete((result, throwable) -> SwingUtilities.invokeLater(() -> {
                    if (requestId != requestSequence.get()) {
                        return;
                    }
                    if (throwable != null || result == null) {
                        updateTerminalHelpLoading(false);
                        clearSuggestions();
                        return;
                    }
                    applySuggestionResult(result);
                }));
    }

    private void applySuggestionResult(SuggestionEngineResult result) {
        lastResult = result;
        if (terminalHelpLoadingRequested) {
            updateTerminalHelpLoading(isTerminalHelpWarmupPending());
        }
        suggestionModel.clear();

        List<Suggestion> suggestions = result.suggestions();
        if (suggestions.isEmpty()) {
            clearSuggestions();
            return;
        }

        for (Suggestion suggestion : suggestions) {
            suggestionModel.addElement(suggestion);
        }
        suggestionList.setSelectedIndex(0);
        suggestionList.ensureIndexIsVisible(0);
        showSuggestionPopup();
    }

    private void showSuggestionPopup() {
        if (suggestionModel.isEmpty()) {
            clearSuggestions();
            return;
        }

        int rowCount = Math.min(MAX_VISIBLE_ROWS, suggestionModel.size());
        int preferredHeight = Math.max(48, rowCount * 28 + 8);
        int preferredWidth = Math.max(Math.min(inputField.getWidth(), 280), 220);
        suggestionScrollPane.setPreferredSize(new Dimension(preferredWidth, preferredHeight));

        JRootPane rootPane = SwingUtilities.getRootPane(inputField);
        int popupY = inputField.getHeight() + POPUP_GAP_PX;
        if (rootPane != null) {
            Point inputPoint = SwingUtilities.convertPoint(inputField, 0, 0, rootPane);
            int availableAbove = inputPoint.y;
            int availableBelow = Math.max(0, rootPane.getHeight() - (inputPoint.y + inputField.getHeight()));
            boolean placeAbove = availableAbove >= preferredHeight + POPUP_GAP_PX || availableAbove > availableBelow;
            if (placeAbove) {
                popupY = -preferredHeight - POPUP_GAP_PX;
            }
        }

        if (!suggestionPopup.isVisible()) {
            suggestionPopup.show(inputField, 0, popupY);
        } else {
            suggestionPopup.pack();
            suggestionPopup.show(inputField, 0, popupY);
        }
    }

    private void moveSelection(int delta) {
        if (!suggestionPopup.isVisible() || suggestionModel.isEmpty()) {
            return;
        }
        int current = suggestionList.getSelectedIndex();
        int next = current < 0 ? 0 : Math.max(0, Math.min(suggestionModel.size() - 1, current + delta));
        suggestionList.setSelectedIndex(next);
        suggestionList.ensureIndexIsVisible(next);
    }

    private void applyTabCompletion() {
        if (!currentContext.serverOnline() || lastResult == null || suggestionModel.isEmpty()) {
            return;
        }

        int selectedIndex = suggestionList.getSelectedIndex();
        if (selectedIndex < 0) {
            selectedIndex = 0;
        }

        TabCompletionPlan plan = lastResult.tabCompletionPlan();
        if (selectedIndex == 0 && plan.mode() == TabCompletionPlan.Mode.APPLY_COMMON_PREFIX) {
            replaceText(plan.replaceStartOffset(), plan.replaceEndOffset(), plan.replacementText());
            return;
        }

        Suggestion selected = suggestionModel.getElementAt(selectedIndex);
        String replacement = selected.insertText();
        if (selected.category() == SuggestionCategory.COMMAND
                && lastResult.parsedLine().slashPrefixed()
                && lastResult.parsedLine().activeTokenStart() == 0) {
            replacement = replacement.startsWith("/") ? replacement : "/" + replacement;
        }
        if (selected.category() == SuggestionCategory.COMMAND && !replacement.endsWith(" ")) {
            replacement = replacement + " ";
        }
        if (selected.category() != SuggestionCategory.COMMAND
                && lastResult.parsedLine().activeTokenStart() == lastResult.parsedLine().activeTokenEnd()
                && lastResult.parsedLine().activeTokenStart() > 0) {
            String currentText = inputField.getText();
            int previousIndex = lastResult.parsedLine().activeTokenStart() - 1;
            if (previousIndex < currentText.length() && !Character.isWhitespace(currentText.charAt(previousIndex))) {
                replacement = " " + replacement;
            }
        }
        replaceText(lastResult.parsedLine().activeTokenStart(), lastResult.parsedLine().activeTokenEnd(), replacement);
    }

    private void replaceText(int start, int end, String replacement) {
        String text = inputField.getText();
        int safeStart = Math.max(0, Math.min(start, text.length()));
        int safeEnd = Math.max(safeStart, Math.min(end, text.length()));

        String updated = text.substring(0, safeStart) + replacement + text.substring(safeEnd);
        inputField.setText(updated);
        inputField.setCaretPosition(safeStart + replacement.length());
        scheduleSuggestionRefresh();
    }

    private void sendCurrentCommand() {
        if (!currentContext.serverOnline()) {
            return;
        }
        String command = inputField.getText().trim();
        if (command.isBlank()) {
            return;
        }
        if (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        if (command.isBlank()) {
            return;
        }
        if ("help".equalsIgnoreCase(command)) {
            startTerminalHelpLoading();
        }
        commandSender.accept(command);
        inputField.setText("");
        clearSuggestions();
    }

    private void handleRuntimeInvalidation() {
        currentContext = contextSynchronizer.getCurrentContext(serverSupplier.get(), onlinePlayersSupplier.get());
        warmupCommandCatalogIfNeeded();
        refreshInteractiveState();
        if (inputField.isFocusOwner() || suggestionPopup.isVisible()) {
            scheduleSuggestionRefresh();
        }
    }

    private void warmupCommandCatalogIfNeeded() {
        if (currentContext == null || !currentContext.serverOnline() || currentContext.minecraftVersion().isBlank()) {
            return;
        }
        vanillaCatalogService.ensureCommandWarmupScheduled(currentContext.minecraftVersion());
    }

    private void refreshInteractiveState() {
        boolean online = currentContext.serverOnline();
        inputField.setEnabled(online);
        inputField.setEditable(online);
        inputField.setFocusable(online);
        inputField.setToolTipText(online ? null : offlinePlaceholder);
        inputField.setForeground(online
                ? AppTheme.getConsoleForeground()
                : AppTheme.withAlpha(AppTheme.getConsoleForeground(), 150));
        if (!online) {
            updateTerminalHelpLoading(false);
            clearSuggestions();
        }
    }

    private boolean isTerminalHelpWarmupPending() {
        return currentContext != null
                && !currentContext.minecraftVersion().isBlank()
                && vanillaCatalogService.isCommandWarmupPending(currentContext.minecraftVersion());
    }

    private void startTerminalHelpLoading() {
        if (currentContext == null || currentContext.minecraftVersion().isBlank()) {
            return;
        }
        vanillaCatalogService.ensureCommandWarmupScheduled(currentContext.minecraftVersion());
        if (vanillaCatalogService.isCommandWarmupPending(currentContext.minecraftVersion())) {
            terminalHelpLoadingRequested = true;
            updateTerminalHelpLoading(true);
        }
    }

    private void updateTerminalHelpLoading(boolean loading) {
        if (terminalHelpLoading == loading) {
            return;
        }
        terminalHelpLoading = loading;
        loadingPanel.setVisible(loading);
        if (loading) {
            if (!loadingRotationTimer.isRunning()) {
                loadingRotationTimer.start();
            }
            if (!loadingPollTimer.isRunning()) {
                loadingPollTimer.start();
            }
        } else {
            terminalHelpLoadingRequested = false;
            loadingPollTimer.stop();
            loadingRotationTimer.stop();
            storeLoadingAngle(0d);
            loadingIcon.setAngleRadians(0d);
            loadingIconLabel.repaint();
        }
        revalidate();
        repaint();
    }

    private double currentLoadingAngle() {
        Object value = loadingIconLabel.getClientProperty("easy-mc-server.loadingAngle");
        return value instanceof Number number ? number.doubleValue() : 0d;
    }

    private void storeLoadingAngle(double angle) {
        loadingIconLabel.putClientProperty("easy-mc-server.loadingAngle", angle);
    }

    private final class SuggestionCellRenderer implements ListCellRenderer<Suggestion> {
        @Override
        public Component getListCellRendererComponent(
                JList<? extends Suggestion> list,
                Suggestion value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setOpaque(true);
            panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

            Color background = isSelected
                    ? AppTheme.withAlpha(AppTheme.darken(AppTheme.getMainAccent(), 0.28f), 235)
                    : AppTheme.getConsoleBackground();
            Color foreground = AppTheme.getConsoleForeground();
            panel.setBackground(background);

            JLabel main = new JLabel(value == null ? "" : buildMainText(value));
            main.setForeground(foreground);
            main.setFont(main.getFont().deriveFont(Font.BOLD, 12f));

            JLabel source = new JLabel(value == null ? "" : resolveSection(value));
            source.setForeground(isSelected
                    ? AppTheme.getConsoleForeground()
                    : AppTheme.withAlpha(AppTheme.getMainAccent(), 235));
            source.setFont(source.getFont().deriveFont(Font.BOLD, 10f));

            panel.add(main, BorderLayout.CENTER);
            panel.add(source, BorderLayout.EAST);
            return panel;
        }

        private String buildMainText(Suggestion suggestion) {
            if (suggestion == null) {
                return "";
            }
            String text = suggestion.displayText();
            if (suggestion.category() == SuggestionCategory.COMMAND) {
                text = text.startsWith("/") ? text : "/" + text;
            }
            return text;
        }

        private String resolveSection(Suggestion suggestion) {
            if (suggestion == null) {
                return "OTROS";
            }
            String source = suggestion.source() == null ? "" : suggestion.source().toLowerCase();
            return switch (suggestion.category()) {
                case PLAYER -> "JUGADORES";
                case COMMAND, SUBCOMMAND -> {
                    if (source.contains("vanilla") || source.contains("fallback")) {
                        yield "VANILLA";
                    }
                    if (source.contains("plugin")) {
                        yield "PLUGIN";
                    }
                    if (source.contains("mod")) {
                        yield "MOD";
                    }
                    yield "COMANDOS";
                }
                case ITEM, BLOCK, ENTITY, TAG, EFFECT, ENCHANTMENT, DIMENSION, GAMERULE -> {
                    if (source.contains("vanilla")) {
                        yield "VANILLA";
                    }
                    if (source.contains("plugin")) {
                        yield "PLUGIN";
                    }
                    if (source.contains("mod")) {
                        yield "MOD";
                    }
                    yield "OTROS";
                }
                default -> {
                    if (source.contains("plugin")) {
                        yield "PLUGIN";
                    }
                    if (source.contains("mod")) {
                        yield "MOD";
                    }
                    yield "OTROS";
                }
            };
        }
    }
}
