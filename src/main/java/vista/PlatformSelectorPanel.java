package vista;

import controlador.platform.ServerPlatformAdapter;
import modelo.extensions.ServerPlatform;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class PlatformSelectorPanel extends JPanel {
    private static final String FALLBACK_ICON = "easymcicons/box.svg";
    private static final int OPTION_WIDTH = 96;
    private static final int OPTION_HEIGHT = 84;
    private static final int ICON_SIZE = 38;

    private final List<ServerPlatformAdapter> adapters;
    private final List<PlatformOptionButton> optionButtons = new ArrayList<>();
    private final ButtonGroup buttonGroup = new ButtonGroup();
    private Consumer<ServerPlatformAdapter> selectionListener;
    private ServerPlatformAdapter selectedAdapter;

    public PlatformSelectorPanel(List<ServerPlatformAdapter> adapters,
                                 ServerPlatformAdapter initialSelection,
                                 Consumer<ServerPlatformAdapter> selectionListener) {
        this.adapters = adapters == null ? List.of() : List.copyOf(adapters);
        this.selectionListener = selectionListener;
        setLayout(new GridBagLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        build();

        ServerPlatformAdapter initial = initialSelection != null && this.adapters.contains(initialSelection)
                ? initialSelection
                : null;
        setSelectedAdapter(initial, false);
    }

    public ServerPlatformAdapter getSelectedAdapter() {
        return selectedAdapter;
    }

    public void setSelectionListener(Consumer<ServerPlatformAdapter> selectionListener) {
        this.selectionListener = selectionListener;
    }

    public void setSelectedAdapter(ServerPlatformAdapter adapter) {
        setSelectedAdapter(adapter, true);
    }

    private void build() {
        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        GridBagConstraints rootGbc = new GridBagConstraints();
        rootGbc.gridx = 0;
        rootGbc.gridy = 0;
        rootGbc.weightx = 1.0;
        rootGbc.weighty = 1.0;
        rootGbc.anchor = GridBagConstraints.NORTH;
        rootGbc.fill = GridBagConstraints.HORIZONTAL;
        add(content, rootGbc);

        int row = 0;
        List<ServerPlatformAdapter> vanilla = sortedAdapters(adapter ->
                adapter.getPlatform() != null && adapter.getPlatform().isVanillaPlatform());
        if (!vanilla.isEmpty()) {
            content.add(createOptionsRow(vanilla), rowConstraints(row++, 0, 0, 0));
        }

        List<ServerPlatformAdapter> mods = sortedAdapters(adapter ->
                adapter.getPlatform() != null && adapter.getPlatform().isModPlatform());
        if (!mods.isEmpty()) {
            content.add(createSectionSeparator("Mods"), rowConstraints(row++, 4, 0, 0));
            content.add(createOptionsRow(mods), rowConstraints(row++, 0, 0, 0));
        }

        List<ServerPlatformAdapter> plugins = sortedAdapters(adapter ->
                adapter.getPlatform() != null && adapter.getPlatform().isPluginPlatform());
        if (!plugins.isEmpty()) {
            content.add(createSectionSeparator("Plugins"), rowConstraints(row++, 4, 0, 0));
            content.add(createOptionsRow(plugins), rowConstraints(row, 0, 0, 0));
        }
    }

    private List<ServerPlatformAdapter> sortedAdapters(java.util.function.Predicate<ServerPlatformAdapter> predicate) {
        return adapters.stream()
                .filter(Objects::nonNull)
                .filter(predicate)
                .sorted(Comparator.comparingInt(adapter -> platformOrder(adapter.getPlatform())))
                .toList();
    }

    private JComponent createOptionsRow(List<ServerPlatformAdapter> rowAdapters) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        for (int i = 0; i < rowAdapters.size(); i++) {
            ServerPlatformAdapter adapter = rowAdapters.get(i);
            PlatformOptionButton button = new PlatformOptionButton(adapter);
            buttonGroup.add(button);
            optionButtons.add(button);
            button.addItemListener(e -> {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    setSelectedAdapter(adapter, true);
                }
            });
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = i;
            gbc.gridy = 0;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(0, 4, 0, 4);
            gbc.anchor = GridBagConstraints.CENTER;
            row.add(button, gbc);
        }
        return row;
    }

    private JComponent createSectionSeparator(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        DividerLine leftLine = new DividerLine();
        GridBagConstraints leftGbc = new GridBagConstraints();
        leftGbc.gridx = 0;
        leftGbc.gridy = 0;
        leftGbc.weightx = 1.0;
        leftGbc.fill = GridBagConstraints.HORIZONTAL;
        leftGbc.insets = new Insets(0, 0, 0, 12);
        panel.add(leftLine, leftGbc);

        JLabel label = new JLabel(title);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 16f));
        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.gridx = 1;
        labelGbc.gridy = 0;
        labelGbc.anchor = GridBagConstraints.CENTER;
        panel.add(label, labelGbc);

        DividerLine rightLine = new DividerLine();
        GridBagConstraints rightGbc = new GridBagConstraints();
        rightGbc.gridx = 2;
        rightGbc.gridy = 0;
        rightGbc.weightx = 1.0;
        rightGbc.fill = GridBagConstraints.HORIZONTAL;
        rightGbc.insets = new Insets(0, 12, 0, 0);
        panel.add(rightLine, rightGbc);

        return panel;
    }

    private GridBagConstraints rowConstraints(int row, int top, int bottom, int horizontal) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(top, horizontal, bottom, horizontal);
        return gbc;
    }

    private void setSelectedAdapter(ServerPlatformAdapter adapter, boolean notify) {
        if (adapter != null && !adapters.contains(adapter)) {
            return;
        }
        boolean changed = !Objects.equals(selectedAdapter, adapter);
        selectedAdapter = adapter;
        if (selectedAdapter == null) {
            buttonGroup.clearSelection();
        }
        for (PlatformOptionButton button : optionButtons) {
            boolean selected = Objects.equals(button.adapter, selectedAdapter);
            if (button.isSelected() != selected) {
                button.setSelected(selected);
            }
            button.refreshVisualState();
        }
        if (notify && changed && selectionListener != null) {
            selectionListener.accept(selectedAdapter);
        }
    }

    private static int platformOrder(ServerPlatform platform) {
        if (platform == null) {
            return 1000;
        }
        return switch (platform) {
            case VANILLA -> 0;
            case FORGE -> 10;
            case NEOFORGE -> 20;
            case FABRIC -> 30;
            case QUILT -> 40;
            case PURPUR -> 110;
            case PAPER -> 120;
            case SPIGOT -> 130;
            case BUKKIT -> 140;
            case PUFFERFISH -> 150;
            case UNKNOWN -> 1000;
        };
    }

    private static String iconResourceFor(ServerPlatformAdapter adapter) {
        ServerPlatform platform = adapter == null ? ServerPlatform.UNKNOWN : adapter.getPlatform();
        String platformName = platform == null ? "" : platform.name().toLowerCase(java.util.Locale.ROOT);
        String resource = "easymcicons/" + platformName + ".svg";
        return resourceExists(resource) ? resource : FALLBACK_ICON;
    }

    private static boolean resourceExists(String resourcePath) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = PlatformSelectorPanel.class.getClassLoader();
        }
        return classLoader != null && classLoader.getResource(resourcePath) != null;
    }

    private static String optionText(ServerPlatformAdapter adapter) {
        String name = adapter == null ? "" : adapter.getCreationDisplayName();
        return escapeHtml(name);
    }

    private static String escapeHtml(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private final class PlatformOptionButton extends JToggleButton {
        private final ServerPlatformAdapter adapter;
        private final String iconResource;
        private boolean hovered;

        private PlatformOptionButton(ServerPlatformAdapter adapter) {
            super(optionText(adapter));
            this.adapter = adapter;
            this.iconResource = iconResourceFor(adapter);
            setHorizontalAlignment(SwingConstants.CENTER);
            setHorizontalTextPosition(SwingConstants.CENTER);
            setVerticalTextPosition(SwingConstants.BOTTOM);
            setIconTextGap(7);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(OPTION_WIDTH, OPTION_HEIGHT));
            setMinimumSize(new Dimension(OPTION_WIDTH, OPTION_HEIGHT));
            setToolTipText(adapter == null ? null : adapter.getCreationDisplayName());
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    refreshVisualState();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    refreshVisualState();
                }
            });
            refreshVisualState();
        }

        private void refreshVisualState() {
            Color foreground = isSelected() ? AppTheme.getMainAccent() : AppTheme.getForeground();
            setForeground(foreground);
            Icon icon = SvgIconFactory.create(iconResource, ICON_SIZE, ICON_SIZE, () -> foreground);
            setIcon(icon);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = Math.max(8, AppTheme.getArc());
            Color fill = isSelected()
                    ? AppTheme.getSoftSelectionBackground()
                    : hovered ? AppTheme.getHoverBackground() : UIManager.getColor("Panel.background");
            if (fill != null && (isSelected() || hovered || isFocusOwner())) {
                g2.setColor(fill);
                g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, arc, arc);
            }

            Color border = isSelected()
                    ? AppTheme.getMainAccent()
                    : isFocusOwner() ? AppTheme.withAlpha(AppTheme.getMainAccent(), 180) : AppTheme.withAlpha(AppTheme.getBorderColor(), 90);
            if (isSelected() || hovered || isFocusOwner()) {
                g2.setColor(border);
                g2.setStroke(new BasicStroke(isSelected() ? 1.5f : 1f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc, arc);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class DividerLine extends JComponent {
        private static final int HEIGHT = 8;

        private DividerLine() {
            setPreferredSize(new Dimension(80, HEIGHT));
            setMinimumSize(new Dimension(24, HEIGHT));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(AppTheme.getForeground());
            g2.setStroke(new BasicStroke(1.5f));
            int y = getHeight() / 2;
            g2.drawLine(0, y, getWidth(), y);
            g2.dispose();
        }
    }
}
