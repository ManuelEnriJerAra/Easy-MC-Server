package vista;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

final class ExtensionDetailsLayout extends JPanel {
    private JScrollPane bodyScrollPane;

    ExtensionDetailsLayout(JLabel iconLabel,
                           JLabel titleLabel,
                           JLabel subtitleLabel,
                           JLabel statusBadgeLabel,
                           JComponent actionComponent,
                           JComponent descriptionArea,
                           JPanel noticePanel,
                           List<JPanel> infoSections,
                           List<JComponent> trailingSections,
                           Runnable repaintCallback) {
        this(iconLabel, titleLabel, subtitleLabel, statusBadgeLabel, actionComponent, descriptionArea,
                noticePanel, infoSections, trailingSections, repaintCallback, true);
    }

    ExtensionDetailsLayout(JLabel iconLabel,
                           JLabel titleLabel,
                           JLabel subtitleLabel,
                           JLabel statusBadgeLabel,
                           JComponent actionComponent,
                           JComponent descriptionArea,
                           JPanel noticePanel,
                           List<JPanel> infoSections,
                           List<JComponent> trailingSections,
                           Runnable repaintCallback,
                           boolean statusBadgeInBody) {
        super(new BorderLayout(0, 12));
        setOpaque(false);

        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);

        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setVerticalAlignment(SwingConstants.CENTER);
        iconLabel.setOpaque(true);
        iconLabel.setBackground(AppTheme.getSurfaceBackground());
        iconLabel.setPreferredSize(new Dimension(56, 56));
        header.add(iconLabel, BorderLayout.WEST);

        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 20f));
        subtitleLabel.setForeground(AppTheme.getMutedForeground());
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(13f));
        titleBlock.add(titleLabel);
        titleBlock.add(Box.createVerticalStrut(6));
        titleBlock.add(subtitleLabel);
        header.add(titleBlock, BorderLayout.CENTER);

        if (actionComponent != null || statusBadgeLabel != null && !statusBadgeInBody) {
            JPanel headerActions = new JPanel();
            headerActions.setOpaque(false);
            headerActions.setLayout(new BoxLayout(headerActions, BoxLayout.X_AXIS));
            if (statusBadgeLabel != null && !statusBadgeInBody) {
                headerActions.add(statusBadgeLabel);
            }
            if (actionComponent != null) {
                if (statusBadgeLabel != null && !statusBadgeInBody) {
                    headerActions.add(Box.createHorizontalStrut(8));
                }
                headerActions.add(actionComponent);
            }
            header.add(headerActions, BorderLayout.EAST);
        }
        add(header, BorderLayout.NORTH);

        JPanel body = new DetailBodyPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        if (statusBadgeLabel != null && statusBadgeInBody) {
            JPanel badgeRow = new FullWidthRow(new BorderLayout(0, 0));
            badgeRow.setOpaque(false);
            badgeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            badgeRow.add(statusBadgeLabel, BorderLayout.CENTER);
            body.add(badgeRow);
            body.add(Box.createVerticalStrut(10));
        }
        if (noticePanel != null) {
            configureFullWidth(noticePanel);
            body.add(noticePanel);
            body.add(Box.createVerticalStrut(10));
        }
        body.add(buildDescriptionPanel(descriptionArea));
        if (infoSections != null) {
            for (JPanel section : infoSections) {
                if (section == null) {
                    continue;
                }
                configureFullWidth(section);
                body.add(Box.createVerticalStrut(10));
                body.add(section);
            }
        }
        if (trailingSections != null) {
            for (JComponent section : trailingSections) {
                if (section == null) {
                    continue;
                }
                configureFullWidth(section);
                body.add(Box.createVerticalStrut(10));
                body.add(section);
            }
        }

        if (descriptionArea instanceof JTextPane textPane) {
            configureDescriptionArea(textPane);
        }
        bodyScrollPane = new JScrollPane(body);
        bodyScrollPane.setBorder(null);
        bodyScrollPane.getViewport().setOpaque(false);
        bodyScrollPane.getViewport().setBackground(AppTheme.getPanelBackground());
        bodyScrollPane.setOpaque(false);
        bodyScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        bodyScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        bodyScrollPane.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                body.revalidate();
                body.repaint();
            }
        });
        add(bodyScrollPane, BorderLayout.CENTER);
    }

    JScrollPane bodyScrollPane() {
        return bodyScrollPane;
    }

    static JPanel buildDescriptionPanel(JComponent descriptionArea) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        configureFullWidth(panel);
        JLabel title = new JLabel("Descripción");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        title.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 225));
        panel.add(title, BorderLayout.NORTH);
        panel.add(descriptionArea, BorderLayout.CENTER);
        return panel;
    }

    static JPanel buildInfoRow(String title, JLabel value) {
        JPanel row = new JPanel(new BorderLayout(0, 4));
        row.setOpaque(false);
        configureFullWidth(row);
        row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        titleLabel.setForeground(AppTheme.getMutedForeground());
        value.setForeground(AppTheme.withAlpha(AppTheme.getForeground(), 215));
        value.setVerticalAlignment(SwingConstants.TOP);
        row.add(titleLabel, BorderLayout.NORTH);
        row.add(value, BorderLayout.CENTER);
        return row;
    }

    static JPanel buildInfoRowWithDivider(String title, JLabel leftValue, JLabel rightValue) {
        JPanel row = buildInfoRow(title, leftValue);
        JPanel values = new JPanel(new GridLayout(1, 2, 8, 0));
        values.setOpaque(false);
        values.add(leftValue);
        values.add(rightValue);
        row.add(values, BorderLayout.CENTER);
        return row;
    }

    static JPanel buildInfoGrid(List<JPanel> rows) {
        JPanel grid = new JPanel(new GridLayout(0, 2, 10, 10));
        grid.setOpaque(false);
        configureFullWidth(grid);
        if (rows != null) {
            for (JPanel row : rows) {
                if (row != null) {
                    grid.add(row);
                }
            }
        }
        return grid;
    }

    static void configureFullWidth(JComponent component) {
        if (component == null) {
            return;
        }
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    static void configureStatusBadge(JLabel badge) {
        if (badge == null) {
            return;
        }
        badge.setOpaque(false);
        badge.setHorizontalAlignment(SwingConstants.CENTER);
        badge.setVerticalAlignment(SwingConstants.CENTER);
        badge.setFont(badge.getFont().deriveFont(Font.BOLD, 11.75f));
        applyStatusBadgeStyle(
                badge,
                AppTheme.withAlpha(AppTheme.getForeground(), 210),
                AppTheme.withAlpha(AppTheme.getForeground(), 12),
                AppTheme.withAlpha(AppTheme.getBorderColor(), 120)
        );
    }

    static void applyStatusBadgeStyle(JLabel badge, Color foreground, Color background, Color borderColor) {
        if (badge == null) {
            return;
        }
        badge.setOpaque(false);
        badge.setHorizontalAlignment(SwingConstants.CENTER);
        badge.setVerticalAlignment(SwingConstants.CENTER);
        badge.setForeground(foreground);
        badge.setBackground(background);
        badge.setBorder(BorderFactory.createEmptyBorder(7, 11, 7, 11));
        if (badge instanceof StatusBadgeLabel statusBadgeLabel) {
            statusBadgeLabel.setBadgeBorderColor(borderColor);
        }
        badge.revalidate();
        badge.repaint();
    }

    static void configureDescriptionArea(JTextPane descriptionArea) {
        descriptionArea.setEditable(false);
        descriptionArea.setOpaque(false);
        descriptionArea.setBorder(null);
        descriptionArea.setMargin(new Insets(0, 0, 0, 10));
        descriptionArea.setFocusable(false);
        descriptionArea.setForeground(AppTheme.getForeground());
        descriptionArea.setFont(descriptionArea.getFont().deriveFont(13.5f));
    }

    static final class DetailBodyPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(320, 240);
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(16, visibleRect.height - 32);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static final class FullWidthRow extends JPanel {
        private FullWidthRow(LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getMaximumSize() {
            Dimension preferred = getPreferredSize();
            return new Dimension(Integer.MAX_VALUE, preferred.height);
        }
    }

    static final class StatusBadgeLabel extends JLabel {
        private String rawText = "";
        private Color badgeBorderColor = AppTheme.withAlpha(AppTheme.getBorderColor(), 120);

        StatusBadgeLabel(String text) {
            super();
            setText(text);
        }

        @Override
        public void setText(String text) {
            rawText = text == null ? "" : text;
            super.setText(null);
            revalidate();
            repaint();
        }

        String rawText() {
            return rawText;
        }

        void setBadgeBorderColor(Color badgeBorderColor) {
            this.badgeBorderColor = badgeBorderColor == null
                    ? AppTheme.withAlpha(AppTheme.getBorderColor(), 120)
                    : badgeBorderColor;
        }

        @Override
        public Dimension getPreferredSize() {
            Insets insets = getInsets();
            FontMetrics metrics = getFontMetrics(getFont());
            int wrapWidth = resolveWrapWidth();
            List<String> lines = wrapLines(metrics, wrapWidth);
            int textWidth = 0;
            for (String line : lines) {
                textWidth = Math.max(textWidth, metrics.stringWidth(line));
            }
            int width = (hasFullWidthRowAncestor() ? wrapWidth : Math.max(1, Math.min(wrapWidth, textWidth)))
                    + insets.left + insets.right;
            int height = Math.max(metrics.getHeight(), lines.size() * metrics.getHeight()) + insets.top + insets.bottom;
            return new Dimension(width, height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int arc = Math.max(10, AppTheme.getArc());
                int width = getWidth() - 1;
                int height = getHeight() - 1;
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, width, height, arc, arc);
                g2.setColor(badgeBorderColor);
                g2.drawRoundRect(0, 0, width, height, arc, arc);

                Insets insets = getInsets();
                FontMetrics metrics = g2.getFontMetrics(getFont());
                List<String> lines = wrapLines(metrics, Math.max(1, getWidth() - insets.left - insets.right));
                int lineHeight = metrics.getHeight();
                int textHeight = lines.size() * lineHeight;
                int y = insets.top + Math.max(0, getHeight() - insets.top - insets.bottom - textHeight) / 2 + metrics.getAscent();
                g2.setFont(getFont());
                g2.setColor(getForeground());
                for (String line : lines) {
                    int lineWidth = metrics.stringWidth(line);
                    int x = insets.left + Math.max(0, getWidth() - insets.left - insets.right - lineWidth) / 2;
                    g2.drawString(line, x, y);
                    y += lineHeight;
                }
            } finally {
                g2.dispose();
            }
        }

        private int resolveWrapWidth() {
            int available = -1;
            Container parent = getParent();
            while (parent != null) {
                if (parent instanceof JViewport viewport && viewport.getWidth() > 0) {
                    available = viewport.getWidth();
                    break;
                }
                if (available <= 0 && parent.getWidth() > 0) {
                    available = parent.getWidth();
                }
                parent = parent.getParent();
            }
            if (available <= 0) {
                FontMetrics metrics = getFontMetrics(getFont());
                available = Math.max(1, maxUnwrappedTextWidth(metrics) + getInsets().left + getInsets().right);
            }
            Insets insets = getInsets();
            return Math.max(1, available - insets.left - insets.right - 4);
        }

        private boolean hasFullWidthRowAncestor() {
            Container parent = getParent();
            while (parent != null) {
                if (parent instanceof FullWidthRow) {
                    return true;
                }
                parent = parent.getParent();
            }
            return false;
        }

        private int maxUnwrappedTextWidth(FontMetrics metrics) {
            String value = rawText == null || rawText.isBlank() ? " " : rawText.trim();
            int width = 1;
            for (String paragraph : value.split("\\R", -1)) {
                width = Math.max(width, metrics.stringWidth(paragraph));
            }
            return width;
        }

        private List<String> wrapLines(FontMetrics metrics, int maxWidth) {
            List<String> lines = new ArrayList<>();
            String value = rawText == null || rawText.isBlank() ? " " : rawText.trim();
            for (String paragraph : value.split("\\R", -1)) {
                appendWrappedParagraph(lines, metrics, paragraph, maxWidth);
            }
            return lines.isEmpty() ? List.of(" ") : lines;
        }

        private void appendWrappedParagraph(List<String> lines, FontMetrics metrics, String paragraph, int maxWidth) {
            if (paragraph == null || paragraph.isBlank()) {
                lines.add(" ");
                return;
            }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split("\\s+")) {
                if (line.isEmpty()) {
                    appendWord(lines, line, metrics, word, maxWidth);
                } else {
                    String candidate = line + " " + word;
                    if (metrics.stringWidth(candidate) <= maxWidth) {
                        line.append(' ').append(word);
                    } else {
                        lines.add(line.toString());
                        line.setLength(0);
                        appendWord(lines, line, metrics, word, maxWidth);
                    }
                }
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
        }

        private void appendWord(List<String> lines, StringBuilder line, FontMetrics metrics, String word, int maxWidth) {
            if (metrics.stringWidth(word) <= maxWidth) {
                line.append(word);
                return;
            }
            StringBuilder chunk = new StringBuilder();
            for (int i = 0; i < word.length(); i++) {
                String candidate = chunk.toString() + word.charAt(i);
                if (!chunk.isEmpty() && metrics.stringWidth(candidate) > maxWidth) {
                    lines.add(chunk.toString());
                    chunk.setLength(0);
                }
                chunk.append(word.charAt(i));
            }
            line.append(chunk);
        }
    }
}
