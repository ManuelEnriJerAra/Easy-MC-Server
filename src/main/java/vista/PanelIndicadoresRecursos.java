package vista;

import controlador.GestorServidores;
import modelo.Server;

import javax.swing.*;
import java.awt.*;

public class PanelIndicadoresRecursos extends JPanel {
    private static final int INDICATOR_ARC = 10;
    private static final int INDICATOR_GAP = 8;
    static final int READABLE_SINGLE_ROW_WIDTH = 320;
    private final GestorServidores gestorServidores;
    private final MetricIndicatorCard cpuCard = new MetricIndicatorCard("CPU");
    private final MetricIndicatorCard ramCard = new MetricIndicatorCard("RAM");
    private final MetricIndicatorCard diskCard = new MetricIndicatorCard("DISCO");
    private final Timer refreshTimer;

    public PanelIndicadoresRecursos(GestorServidores gestorServidores) {
        this.gestorServidores = gestorServidores;
        setOpaque(false);
        setLayout(new GridLayout(1, 3, INDICATOR_GAP, 0));
        add(cpuCard);
        add(ramCard);
        add(diskCard);
        refreshTimer = new Timer(1000, e -> refreshMetrics());
        refreshTimer.setInitialDelay(0);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        refreshMetrics();
        if (!refreshTimer.isRunning()) {
            refreshTimer.start();
        }
    }

    @Override
    public void removeNotify() {
        refreshTimer.stop();
        super.removeNotify();
    }

    private void refreshMetrics() {
        Server server = gestorServidores != null ? gestorServidores.getServidorSeleccionado() : null;
        ServerResourceSnapshot snapshot = PanelEstadisticas.getLiveResourceSnapshot(server);
        cpuCard.updateValue(snapshot.cpuPercentRounded(), snapshot.running());
        ramCard.updateValue(snapshot.ramPercentRounded(), snapshot.running());
        diskCard.updateValue(snapshot.diskPercentRounded(), snapshot.running());
    }

    private static final class MetricIndicatorCard extends JPanel {
        private static final Insets CARD_INSETS = new Insets(8, 8, 8, 8);
        private static final int LABEL_GAP = 8;
        private static final int MINIMUM_CARD_WIDTH = 94;
        private final JLabel metricLabel = new JLabel("-");
        private final JLabel percentLabel = new JLabel("-");
        private final FillBar progressBar = new FillBar();
        private final String title;

        private MetricIndicatorCard(String title) {
            this.title = title == null ? "" : title;
            setOpaque(true);
            setBackground(AppTheme.getPanelBackground());
            setLayout(new BorderLayout(0, 6));
            setBorder(AppTheme.createRoundedBorder(CARD_INSETS, AppTheme.getBorderColor(), 1f, INDICATOR_ARC));

            JPanel labelRow = new JPanel(new BorderLayout(8, 0));
            labelRow.setOpaque(false);

            metricLabel.setText(this.title);
            metricLabel.setForeground(AppTheme.getMutedForeground());
            metricLabel.setFont(metricLabel.getFont().deriveFont(Font.BOLD, 10f));
            metricLabel.setHorizontalAlignment(SwingConstants.LEFT);

            percentLabel.setForeground(AppTheme.getForeground());
            percentLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            percentLabel.setFont(percentLabel.getFont().deriveFont(Font.BOLD, 14f));

            labelRow.add(metricLabel, BorderLayout.WEST);
            labelRow.add(percentLabel, BorderLayout.EAST);

            progressBar.setPreferredSize(new Dimension(0, 8));

            add(labelRow, BorderLayout.CENTER);
            add(progressBar, BorderLayout.SOUTH);
            updateMetricSizing();
        }

        private void updateValue(int percent, boolean running) {
            int safePercent = Math.max(0, Math.min(100, percent));
            percentLabel.setText(safePercent + "%");
            updateMetricSizing();
            progressBar.setValue(safePercent);
            Color accent = running ? ResourcePalette.colorForPercent(percent) : AppTheme.getMainAccent();
            progressBar.setColors(
                    AppTheme.tint(AppTheme.getPanelBackground(), AppTheme.getForeground(), 0.10f),
                    accent
            );
            repaint();
        }

        private void updateMetricSizing() {
            Dimension metricSize = fullTextSize(metricLabel, title);
            metricLabel.setMinimumSize(metricSize);
            metricLabel.setPreferredSize(metricSize);

            Dimension percentSize = fullTextSize(percentLabel, "100%");
            percentLabel.setMinimumSize(percentSize);
            percentLabel.setPreferredSize(percentSize);

            int contentWidth = metricSize.width + LABEL_GAP + percentSize.width;
            int width = Math.max(MINIMUM_CARD_WIDTH, contentWidth + CARD_INSETS.left + CARD_INSETS.right);
            Dimension progressSize = progressBar == null ? new Dimension(0, 8) : progressBar.getPreferredSize();
            int height = CARD_INSETS.top + CARD_INSETS.bottom
                    + Math.max(metricSize.height, percentSize.height)
                    + 6
                    + Math.max(8, progressSize == null ? 8 : progressSize.height);
            Dimension cardSize = new Dimension(width, height);
            setMinimumSize(cardSize);
            setPreferredSize(cardSize);
        }

        private Dimension fullTextSize(JLabel label, String text) {
            Font font = label.getFont();
            FontMetrics metrics = label.getFontMetrics(font);
            Insets insets = label.getInsets();
            int horizontalInsets = insets == null ? 0 : insets.left + insets.right;
            int verticalInsets = insets == null ? 0 : insets.top + insets.bottom;
            return new Dimension(metrics.stringWidth(text == null ? "" : text) + horizontalInsets,
                    metrics.getHeight() + verticalInsets);
        }

        @Override
        public void updateUI() {
            super.updateUI();
            setBackground(AppTheme.getPanelBackground());
            setBorder(AppTheme.createRoundedBorder(CARD_INSETS, AppTheme.getBorderColor(), 1f, INDICATOR_ARC));
            if (metricLabel != null) {
                metricLabel.setForeground(AppTheme.getMutedForeground());
            }
            if (percentLabel != null) {
                percentLabel.setForeground(AppTheme.getForeground());
            }
            if (progressBar != null) {
                progressBar.setColors(
                        AppTheme.tint(AppTheme.getPanelBackground(), AppTheme.getForeground(), 0.10f),
                        AppTheme.getMainAccent()
                );
            }
            if (metricLabel != null && percentLabel != null) {
                updateMetricSizing();
            }
        }
    }

    private static final class FillBar extends JComponent {
        private int value;
        private Color trackColor = AppTheme.tint(AppTheme.getPanelBackground(), AppTheme.getForeground(), 0.10f);
        private Color fillColor = AppTheme.getMainAccent();

        private void setValue(int value) {
            this.value = Math.max(0, Math.min(100, value));
            repaint();
        }

        private void setColors(Color trackColor, Color fillColor) {
            this.trackColor = trackColor != null ? trackColor : AppTheme.getPanelBackground();
            this.fillColor = fillColor != null ? fillColor : AppTheme.getMainAccent();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int arc = INDICATOR_ARC;
                int width = Math.max(1, getWidth() - 1);
                int height = Math.max(1, getHeight() - 1);
                int fillWidth = Math.max(0, (int) Math.round(width * (value / 100d)));

                g2.setColor(trackColor);
                g2.fillRoundRect(0, 0, width, height, arc, arc);

                if (fillWidth > 0) {
                    g2.setClip(new Rectangle(0, 0, fillWidth, height));
                    g2.setColor(fillColor);
                    g2.fillRoundRect(0, 0, width, height, arc, arc);
                    g2.setClip(null);
                }
            } finally {
                g2.dispose();
            }
        }
    }
}
