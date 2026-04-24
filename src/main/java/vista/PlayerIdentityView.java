package vista;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import controlador.MojangAPI;

public class PlayerIdentityView extends JPanel {
    public enum SizePreset {
        COMPACT(24, 13f, 7, new Insets(0, 0, 0, 4)),
        REGULAR(32, 16f, 8, new Insets(0, 0, 0, 6)),
        LARGE(36, 18f, 10, new Insets(0, 10, 0, 8));

        final int avatarSize;
        final float fontSize;
        final int gap;
        final Insets padding;

        SizePreset(int avatarSize, float fontSize, int gap, Insets padding) {
            this.avatarSize = avatarSize;
            this.fontSize = fontSize;
            this.gap = gap;
            this.padding = padding;
        }
    }

    private static final MojangAPI MOJANG_API = new MojangAPI();
    private static final Map<String, ImageIcon> PLAYER_HEAD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, List<Runnable>> PLAYER_HEAD_WAITERS = new ConcurrentHashMap<>();

    private final JLabel avatarLabel = new JLabel();
    private final JLabel nameLabel = new JLabel();
    private final JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    private final JPanel actionsWrap = new JPanel(new GridBagLayout());
    private final List<JButton> actionButtons = new ArrayList<>();
    private final Map<JButton, String> actionButtonIcons = new ConcurrentHashMap<>();
    private SizePreset sizePreset;
    private String username;
    private boolean actionsVisibleOnHover = false;
    private boolean pointerInside;

    public PlayerIdentityView(String username, SizePreset sizePreset) {
        super(new BorderLayout());
        setOpaque(true);
        avatarLabel.setHorizontalAlignment(SwingConstants.CENTER);
        avatarLabel.setVerticalAlignment(SwingConstants.CENTER);

        nameLabel.setVerticalAlignment(SwingConstants.CENTER);
        actionsPanel.setOpaque(false);
        actionsWrap.setOpaque(false);
        actionsWrap.add(actionsPanel);

        setSizePreset(sizePreset == null ? SizePreset.REGULAR : sizePreset);
        add(avatarLabel, BorderLayout.WEST);
        add(nameLabel, BorderLayout.CENTER);
        add(actionsWrap, BorderLayout.EAST);
        setPlayerName(username);
        setHighlighted(false);
        installHoverTracking(this);
        installHoverTracking(avatarLabel);
        installHoverTracking(nameLabel);
        installHoverTracking(actionsWrap);
        installHoverTracking(actionsPanel);
    }

    public void setPlayerName(String username) {
        this.username = username == null ? "" : username.strip();
        nameLabel.setText(this.username);
        aplicarCabezaJugadorAsync(this.username);
    }

    public String getPlayerName() {
        return username;
    }

    public void setSizePreset(SizePreset sizePreset) {
        this.sizePreset = sizePreset == null ? SizePreset.REGULAR : sizePreset;
        avatarLabel.setPreferredSize(new Dimension(this.sizePreset.avatarSize, this.sizePreset.avatarSize));
        avatarLabel.setMinimumSize(new Dimension(this.sizePreset.avatarSize, this.sizePreset.avatarSize));
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, this.sizePreset.fontSize));
        setBorder(BorderFactory.createEmptyBorder(
                this.sizePreset.padding.top,
                this.sizePreset.padding.left,
                this.sizePreset.padding.bottom,
                this.sizePreset.padding.right
        ));
        ((BorderLayout) getLayout()).setHgap(this.sizePreset.gap);
        refreshActionButtonIcons();
        revalidate();
        repaint();
    }

    public void setHighlighted(boolean highlighted) {
        Color background = highlighted ? AppTheme.getSoftSelectionBackground() : AppTheme.getPanelBackground();
        Color borderColor = highlighted ? AppTheme.getMainAccent() : AppTheme.getSubtleBorderColor();
        setBackground(background);
        setBorder(BorderFactory.createCompoundBorder(
                AppTheme.createRoundedBorder(sizePreset.padding, borderColor, highlighted ? 1.1f : 1f),
                BorderFactory.createEmptyBorder(
                        sizePreset.padding.top,
                        sizePreset.padding.left,
                        sizePreset.padding.bottom,
                        sizePreset.padding.right
                )
        ));
    }

    public JLabel getNameLabel() {
        return nameLabel;
    }

    public JButton addActionButton(String iconPath, String tooltip, Runnable onClick) {
        JButton button = new JButton();
        AppTheme.applyHeaderIconButtonStyle(button);
        actionButtonIcons.put(button, iconPath);
        applyActionButtonIcon(button, iconPath);
        button.setToolTipText(tooltip);
        if (onClick != null) {
            button.addActionListener(e -> onClick.run());
        }
        actionButtons.add(button);
        actionsPanel.add(button);
        installHoverTracking(button);
        updateActionsVisibility();
        revalidate();
        repaint();
        return button;
    }

    public void clearActionButtons() {
        actionButtonIcons.clear();
        actionButtons.clear();
        actionsPanel.removeAll();
        updateActionsVisibility();
        revalidate();
        repaint();
    }

    public JPanel getActionsPanel() {
        return actionsPanel;
    }

    public void addPrimaryMouseListener(MouseListener listener) {
        if (listener == null) {
            return;
        }
        addMouseListener(listener);
        avatarLabel.addMouseListener(listener);
        nameLabel.addMouseListener(listener);
        actionsWrap.addMouseListener(listener);
        actionsPanel.addMouseListener(listener);
    }

    public void setActionsVisibleOnHover(boolean actionsVisibleOnHover) {
        this.actionsVisibleOnHover = actionsVisibleOnHover;
        updateActionsVisibility();
    }

    public boolean isActionsVisibleOnHover() {
        return actionsVisibleOnHover;
    }

    public void setActionsVisible(boolean visible) {
        pointerInside = visible;
        updateActionsVisibility();
    }

    private void aplicarCabezaJugadorAsync(String username) {
        if (username == null || username.isBlank()) {
            avatarLabel.setText("?");
            avatarLabel.setIcon(null);
            return;
        }

        String key = buildCacheKey(username, sizePreset.avatarSize);
        ImageIcon cachedHead = PLAYER_HEAD_CACHE.get(key);
        if (cachedHead != null && cachedHead.getImage() != null) {
            avatarLabel.setText(null);
            avatarLabel.setIcon(cachedHead);
            return;
        }

        avatarLabel.setText(obtenerInicial(username));
        avatarLabel.setIcon(null);
        avatarLabel.setFont(avatarLabel.getFont().deriveFont(Font.BOLD, Math.max(11f, sizePreset.fontSize - 1f)));

        requestHead(username, sizePreset.avatarSize, () -> {
            if (!username.equals(this.username)) {
                return;
            }
            ImageIcon loadedHead = PLAYER_HEAD_CACHE.get(key);
            if (loadedHead != null && loadedHead.getImage() != null) {
                avatarLabel.setText(null);
                avatarLabel.setIcon(loadedHead);
            } else {
                avatarLabel.setText(obtenerInicial(username));
                avatarLabel.setIcon(null);
            }
            avatarLabel.revalidate();
            avatarLabel.repaint();
            revalidate();
            repaint();
        });
    }

    public static void preloadHeads(List<String> usernames, int avatarSize, Runnable onAllLoaded) {
        if (usernames == null || usernames.isEmpty()) {
            if (onAllLoaded != null) {
                SwingUtilities.invokeLater(onAllLoaded);
            }
            return;
        }

        List<String> resolvedUsernames = usernames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::strip)
                .distinct()
                .toList();
        if (resolvedUsernames.isEmpty()) {
            if (onAllLoaded != null) {
                SwingUtilities.invokeLater(onAllLoaded);
            }
            return;
        }

        final int[] pending = {0};
        Runnable completion = () -> {
            synchronized (pending) {
                pending[0]--;
                if (pending[0] == 0 && onAllLoaded != null) {
                    SwingUtilities.invokeLater(onAllLoaded);
                }
            }
        };

        synchronized (pending) {
            pending[0] = resolvedUsernames.size();
        }
        for (String username : resolvedUsernames) {
            requestHead(username, avatarSize, completion);
        }
    }

    public static List<String> getCachedUsernames() {
        Set<String> usernames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String key : PLAYER_HEAD_CACHE.keySet()) {
            if (key == null || key.isBlank()) continue;
            int separatorIndex = key.lastIndexOf('@');
            String username = separatorIndex > 0 ? key.substring(0, separatorIndex) : key;
            if (!username.isBlank()) {
                usernames.add(username);
            }
        }
        return new ArrayList<>(usernames);
    }

    private String obtenerInicial(String username) {
        if (username == null || username.isBlank()) {
            return "?";
        }
        return username.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private void refreshActionButtonIcons() {
        for (Map.Entry<JButton, String> entry : actionButtonIcons.entrySet()) {
            applyActionButtonIcon(entry.getKey(), entry.getValue());
        }
    }

    private void applyActionButtonIcon(JButton button, String iconPath) {
        if (button == null || iconPath == null || iconPath.isBlank()) {
            return;
        }
        int iconSize = switch (sizePreset == null ? SizePreset.REGULAR : sizePreset) {
            case COMPACT -> 14;
            case REGULAR -> 18;
            case LARGE -> 24;
        };
        SvgIconFactory.apply(button, iconPath, iconSize, iconSize, AppTheme::getForeground);
    }

    private void updateActionsVisibility() {
        boolean hasActions = !actionButtons.isEmpty();
        boolean shouldShow = hasActions && (!actionsVisibleOnHover || pointerInside);
        actionsWrap.setVisible(shouldShow);
        actionsPanel.setVisible(shouldShow);
        for (JButton button : actionButtons) {
            button.setVisible(shouldShow);
        }
    }

    private void installHoverTracking(Component component) {
        if (component == null) {
            return;
        }
        MouseAdapter hoverAdapter = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                pointerInside = true;
                updateActionsVisibility();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                SwingUtilities.invokeLater(() -> {
                    if (isPointerInsidePlayerIdentityView()) {
                        return;
                    }
                    pointerInside = false;
                    updateActionsVisibility();
                });
            }
        };
        component.addMouseListener(hoverAdapter);
    }

    private boolean isPointerInsidePlayerIdentityView() {
        java.awt.PointerInfo pointerInfo = java.awt.MouseInfo.getPointerInfo();
        if (pointerInfo == null) {
            return false;
        }
        java.awt.Point location = pointerInfo.getLocation();
        SwingUtilities.convertPointFromScreen(location, this);
        return contains(location);
    }

    private static void requestHead(String username, int avatarSize, Runnable onLoaded) {
        if (username == null || username.isBlank()) {
            if (onLoaded != null) {
                SwingUtilities.invokeLater(onLoaded);
            }
            return;
        }

        String key = buildCacheKey(username, avatarSize);
        ImageIcon cachedHead = PLAYER_HEAD_CACHE.get(key);
        if (cachedHead != null && cachedHead.getImage() != null) {
            if (onLoaded != null) {
                SwingUtilities.invokeLater(onLoaded);
            }
            return;
        }

        boolean shouldStartLoading = false;
        if (onLoaded != null) {
            List<Runnable> waiters = PLAYER_HEAD_WAITERS.computeIfAbsent(key, ignored -> Collections.synchronizedList(new ArrayList<>()));
            synchronized (waiters) {
                waiters.add(onLoaded);
                if (waiters.size() == 1) {
                    shouldStartLoading = true;
                }
            }
        } else if (!PLAYER_HEAD_WAITERS.containsKey(key)) {
            PLAYER_HEAD_WAITERS.put(key, Collections.synchronizedList(new ArrayList<>()));
            shouldStartLoading = true;
        }

        if (!shouldStartLoading) {
            return;
        }

        MojangAPI.runBackgroundRequest(() -> {
            try {
                ImageIcon head = MOJANG_API.obtenerCabezaJugador(username, avatarSize);
                if (head != null && head.getImage() != null) {
                    PLAYER_HEAD_CACHE.put(key, head);
                }
            } finally {
                List<Runnable> waiters = PLAYER_HEAD_WAITERS.remove(key);
                if (waiters == null || waiters.isEmpty()) {
                    return;
                }
                List<Runnable> callbacks;
                synchronized (waiters) {
                    callbacks = new ArrayList<>(waiters);
                }
                SwingUtilities.invokeLater(() -> callbacks.forEach(Runnable::run));
            }
        });
    }

    private static String buildCacheKey(String username, int avatarSize) {
        return username.strip().toLowerCase(Locale.ROOT) + "@" + avatarSize;
    }
}
