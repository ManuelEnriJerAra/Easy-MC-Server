package vista;

import controlador.GestorServidores;
import modelo.Server;

import javax.swing.*;
import java.awt.*;

public class PanelIndicadoresRecursos extends JPanel {
    private static final int INDICATOR_ARC = 10;
    private final GestorServidores gestorServidores;
    private final MetricIndicatorCard cpuCard = new MetricIndicatorCard("CPU");
    private final MetricIndicatorCard ramCard = new MetricIndicatorCard("RAM");
    private final MetricIndicatorCard diskCard = new MetricIndicatorCard("DISCO");
    private final Timer refreshTimer;

    public PanelIndicadoresRecursos(GestorServidores gestorServidores) {
        this.gestorServidores = gestorServidores;
        setOpaque(false);
        setLayout(new GridLayout(1, 3, 8, 0));
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
        cpuCard.updateValue(snapshot.cpuPercentRounded(), "CPU " + snapshot.cpuPercentRounded() + "%", snapshot.running());
        ramCard.updateValue(snapshot.ramPercentRounded(), "RAM " + snapshot.ramPercentRounded() + "%", snapshot.running());
        diskCard.updateValue(snapshot.diskPercentRounded(), "DISCO " + snapshot.diskPercentRounded() + "%", snapshot.running());
    }

    private static final class MetricIndicatorCard extends JPanel {
        private static final Insets CARD_INSETS = new Insets(8, 8, 8, 8);
        private final JLabel metricLabel = new JLabel("-");
        private final JLabel percentLabel = new JLabel("-");
        private final FillBar progressBar = new FillBar();

        private MetricIndicatorCard(String title) {
            setOpaque(true);
            setBackground(AppTheme.getSurfaceBackground());
            setLayout(new BorderLayout(0, 6));
            setBorder(AppTheme.createRoundedBorder(CARD_INSETS, AppTheme.getBorderColor(), 1f, INDICATOR_ARC));

            JPanel labelRow = new JPanel(new BorderLayout(8, 0));
            labelRow.setOpaque(false);

            metricLabel.setText(title);
            metricLabel.setForeground(AppTheme.getMutedForeground());
            metricLabel.setFont(metricLabel.getFont().deriveFont(Font.BOLD, 10f));

            percentLabel.setForeground(AppTheme.getForeground());
            percentLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            percentLabel.setFont(percentLabel.getFont().deriveFont(Font.BOLD, 14f));

            labelRow.add(metricLabel, BorderLayout.WEST);
            labelRow.add(percentLabel, BorderLayout.EAST);

            progressBar.setPreferredSize(new Dimension(0, 8));

            add(labelRow, BorderLayout.CENTER);
            add(progressBar, BorderLayout.SOUTH);
        }

        private void updateValue(int percent, String text, boolean running) {
            int safePercent = Math.max(0, Math.min(100, percent));
            percentLabel.setText(safePercent + "%");
            progressBar.setValue(safePercent);
            Color accent = running ? ResourcePalette.colorForPercent(percent) : AppTheme.getMainAccent();
            progressBar.setColors(
                    AppTheme.tint(AppTheme.getSurfaceBackground(), AppTheme.getForeground(), 0.10f),
                    accent
            );
            repaint();
        }

        @Override
        public void updateUI() {
            super.updateUI();
            setBackground(AppTheme.getSurfaceBackground());
            setBorder(AppTheme.createRoundedBorder(CARD_INSETS, AppTheme.getBorderColor(), 1f, INDICATOR_ARC));
            if (metricLabel != null) {
                metricLabel.setForeground(AppTheme.getMutedForeground());
            }
            if (percentLabel != null) {
                percentLabel.setForeground(AppTheme.getForeground());
            }
            if (progressBar != null) {
                progressBar.setColors(
                        AppTheme.tint(AppTheme.getSurfaceBackground(), AppTheme.getForeground(), 0.10f),
                        AppTheme.getMainAccent()
                );
            }
        }
    }

    private static final class FillBar extends JComponent {
        private int value;
        private Color trackColor = AppTheme.tint(AppTheme.getSurfaceBackground(), AppTheme.getForeground(), 0.10f);
        private Color fillColor = AppTheme.getMainAccent();

        private void setValue(int value) {
            this.value = Math.max(0, Math.min(100, value));
            repaint();
        }

        private void setColors(Color trackColor, Color fillColor) {
            this.trackColor = trackColor != null ? trackColor : AppTheme.getSurfaceBackground();
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
