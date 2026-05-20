package vista;

import com.formdev.flatlaf.extras.components.FlatButton;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.text.AttributedString;

final class PanelInformacion extends JPanel {
    private static final int LOGO_SIZE = 104;
    private static final int DORA_REAL_IMAGE_WIDTH = 300;
    private static final int DORA_REAL_IMAGE_HEIGHT = 128;
    private static final int MERCEDARIAS_IMAGE_WIDTH = 128;
    private static final int MERCEDARIAS_IMAGE_HEIGHT = 90;
    private static final int SIDE_COLUMN_WIDTH = 280;
    private static final int LINK_BUTTON_HEIGHT = 34;
    private static final String APP_VERSION = "0.7.1-beta";

    PanelInformacion() {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder());
        add(crearScrollContenido(), BorderLayout.CENTER);
    }

    private JComponent crearScrollContenido() {
        JPanel content = new ViewportWidthPanel(new GridBagLayout());
        content.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 8, 0);
        content.add(crearEncabezado(), gbc);

        gbc.gridy = 1;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, 0);
        content.add(crearDetalle(), gbc);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        AppTheme.applyStandardScrollSpeed(scrollPane);
        return scrollPane;
    }

    private CardPanel crearEncabezado() {
        CardPanel card = new CardPanel("Información");
        card.setBorder(BorderFactory.createEmptyBorder());

        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridheight = 3;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 0, 0, 18);
        body.add(crearLogo(), gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridheight = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 0, 0, 0);
        body.add(crearTitulo(), gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(4, 0, 0, 0);
        body.add(crearTexto(
                "Dora ayuda a crear, importar, ejecutar y organizar servidores de Minecraft desde una aplicación de escritorio. "
                        + "Reúne consola, configuración, mundos, jugadores, extensiones, estadísticas y automatización en una interfaz visual y compacta."
        ), gbc);

        gbc.gridy = 2;
        gbc.weighty = 1.0;
        gbc.insets = new Insets(6, 0, 0, 0);
        body.add(crearVersion(), gbc);

        card.getContentPanel().add(body, BorderLayout.CENTER);
        return card;
    }

    private JComponent crearLogo() {
        JLabel logo = new JLabel(cargarImagen("/doraapp/logo.png", LOGO_SIZE, LOGO_SIZE));
        logo.setHorizontalAlignment(SwingConstants.CENTER);
        logo.setVerticalAlignment(SwingConstants.CENTER);
        Dimension size = new Dimension(LOGO_SIZE, LOGO_SIZE);
        logo.setPreferredSize(size);
        logo.setMinimumSize(size);
        return logo;
    }

    private JComponent crearTitulo() {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.BASELINE_LEADING;
        gbc.insets = new Insets(0, 0, 0, 14);

        JLabel title = new JLabel("Dora");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 28f));
        title.setForeground(AppTheme.getCardTitleColor());
        row.add(title, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JLabel subtitle = new JLabel("Gestor de servidores de Minecraft");
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 16f));
        subtitle.setForeground(AppTheme.getMutedForeground());
        row.add(subtitle, gbc);

        return row;
    }

    private JComponent crearVersion() {
        JLabel label = new JLabel(APP_VERSION);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 16f));
        label.setForeground(AppTheme.getMutedForeground());
        return label;
    }

    private JPanel crearDetalle() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;

        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 0, 8);
        panel.add(crearSobreDoraCard(), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.0;
        gbc.insets = new Insets(0, 0, 0, 0);
        CardPanel sobreMi = crearSobreMiCard();
        sobreMi.setPreferredSize(new Dimension(SIDE_COLUMN_WIDTH, 1));
        sobreMi.setMinimumSize(new Dimension(SIDE_COLUMN_WIDTH, 1));
        panel.add(sobreMi, gbc);

        return panel;
    }

    private CardPanel crearSobreDoraCard() {
        CardPanel card = new CardPanel("Sobre Dora");
        card.setBorder(BorderFactory.createEmptyBorder());

        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 0, 10, 18);
        body.add(crearImagenDoraReal(), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 10, 0);
        body.add(crearTexto(
                "El nombre de la aplicación viene de mi gata Dora. Inicialmente el nombre era muy descriptivo (Easy-MC-Server), pero según avanzaba el proyecto, quería que tuviese algo sobre mí, algo más personal y considerando que el logo era mi gata, decidí llamarlo Dora directamente. Es un nombre genérico, pero quería que mi gata quedase siempre presente en este proyecto.\n"
                        + "Dora falleció en el verano de 2025, pero ahora hay una parte de ella inmortalizada."
        ), gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, 0);
        body.add(Box.createGlue(), gbc);

        gbc.gridy = 2;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        body.add(crearOrigenPanel(), gbc);

        card.getContentPanel().add(body, BorderLayout.CENTER);
        return card;
    }

    private JComponent crearImagenDoraReal() {
        JLabel image = new JLabel(cargarImagen("/doraapp/dora-real.jpeg", DORA_REAL_IMAGE_WIDTH, DORA_REAL_IMAGE_HEIGHT));
        image.setHorizontalAlignment(SwingConstants.CENTER);
        image.setVerticalAlignment(SwingConstants.CENTER);
        Dimension size = new Dimension(DORA_REAL_IMAGE_WIDTH, DORA_REAL_IMAGE_HEIGHT);
        image.setPreferredSize(size);
        image.setMinimumSize(size);
        return image;
    }

    private JComponent crearOrigenPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.SOUTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 12);

        JLabel text = new JLabel("Este proyecto surgió como un TFC para la escuela Mercedarias FP Granada.");
        text.setForeground(AppTheme.getMutedForeground());
        panel.add(text, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(crearMercedariasLinkButton(), gbc);

        return panel;
    }

    private JButton crearMercedariasLinkButton() {
        JButton button = new FlatButton();
        button.setIcon(cargarImagen("/doraapp/mercedarias.png", MERCEDARIAS_IMAGE_WIDTH, MERCEDARIAS_IMAGE_HEIGHT));
        button.setToolTipText("Abrir Mercedarias Granada FP");
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> abrirEnlace(DoraAboutLinks.MERCEDARIAS_URL));
        return button;
    }

    private CardPanel crearSobreMiCard() {
        CardPanel card = new CardPanel("Sobre mí");
        card.setBorder(BorderFactory.createEmptyBorder());

        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        body.add(crearPerfilPanel(), gbc);

        gbc.gridy = 1;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        body.add(Box.createGlue(), gbc);

        gbc.gridy = 2;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        body.add(crearLinksPanel(), gbc);

        card.getContentPanel().add(body, BorderLayout.CENTER);
        return card;
    }

    private JComponent crearPerfilPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel name = new JLabel("ManuelEnriJerAra");
        name.setFont(name.getFont().deriveFont(Font.BOLD, 18f));
        name.setForeground(AppTheme.getForeground());
        name.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(name);
        panel.add(Box.createVerticalStrut(3));

        JLabel role = new JLabel("Desarrollador de Dora");
        role.setForeground(AppTheme.getMutedForeground());
        role.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(role);
        panel.add(Box.createVerticalStrut(10));

        panel.add(crearTexto(
                "Soy Manuel Enrique Jerónimo Aragón, creador de Dora. Este proyecto surgió como una prueba de que era capaz de hacer en Java y decidí aprovecharlo para gestionar mis servidores. El proyecto ha acabado abarcando mucho más de lo que esperaba y ha acabado teniendo un resultado del que estoy muy orgulloso."
        ));
        panel.add(Box.createVerticalStrut(10));
        panel.add(crearInfoFila("Email", DoraAboutLinks.SUPPORT_EMAIL));
        return panel;
    }

    private JPanel crearLinksPanel() {
        JPanel links = new JPanel();
        links.setOpaque(false);
        links.setLayout(new BoxLayout(links, BoxLayout.Y_AXIS));
        links.add(crearLinkButton("GitHub", "doraicons/github.svg", DoraAboutLinks.GITHUB_URL, "Abrir repositorio de Dora"));
        links.add(Box.createVerticalStrut(6));
        links.add(crearLinkButton("LinkedIn", "doraicons/linkedin.svg", DoraAboutLinks.LINKEDIN_URL, "Abrir perfil de LinkedIn"));
        links.add(Box.createVerticalStrut(6));
        links.add(crearLinkButton("Ko-fi", "doraicons/ko-fi.svg", DoraAboutLinks.KOFI_URL, "Abrir Ko-fi"));
        links.add(Box.createVerticalStrut(6));
        links.add(crearLinkButton("Reddit", "doraicons/reddit.svg", DoraAboutLinks.REDDIT_URL, "Abrir perfil de Reddit"));
        return links;
    }

    private JPanel crearInfoFila(String labelText, String valueText) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);

        JLabel label = new JLabel(labelText + ":");
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        row.add(label, BorderLayout.WEST);

        JLabel value = new JLabel(valueText);
        value.setForeground(AppTheme.getForeground());
        row.add(value, BorderLayout.CENTER);
        return row;
    }

    private JComponent crearTexto(String text) {
        WrappedTextBlock block = new WrappedTextBlock(text);
        block.setForeground(AppTheme.getForeground());
        block.setFont(UIManager.getFont("Label.font"));
        block.setAlignmentX(LEFT_ALIGNMENT);
        return block;
    }

    private JButton crearLinkButton(String text, String iconPath, String url, String tooltip) {
        JButton button = new FlatButton();
        button.setText(text);
        button.setToolTipText(tooltip);
        button.setIcon(SvgIconFactory.createOriginal(iconPath, 20, 20));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setHorizontalTextPosition(SwingConstants.RIGHT);
        button.setIconTextGap(10);
        AppTheme.applyActionButtonStyle(button);
        ajustarBotonEnlace(button);
        button.addActionListener(e -> abrirEnlace(url));
        return button;
    }

    private void ajustarBotonEnlace(JButton button) {
        Dimension size = new Dimension(1, LINK_BUTTON_HEIGHT);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, LINK_BUTTON_HEIGHT));
        button.setAlignmentX(LEFT_ALIGNMENT);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private ImageIcon cargarImagen(String resourcePath, int width, int height) {
        URL urlImagen = getClass().getResource(resourcePath);
        if (urlImagen == null) {
            return null;
        }
        ImageIcon icon = new ImageIcon(urlImagen);
        Image image = icon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
        return new ImageIcon(image);
    }

    private void abrirEnlace(String url) {
        if (url == null || url.isBlank() || !Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            mostrarErrorApertura();
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (IllegalArgumentException | IOException ex) {
            mostrarErrorApertura();
        }
    }

    private void mostrarErrorApertura() {
        JOptionPane.showMessageDialog(this, "No se ha podido abrir el enlace.", "Abrir enlace", JOptionPane.WARNING_MESSAGE);
    }

    private static final class WrappedTextBlock extends JComponent {
        private static final int FALLBACK_WRAP_WIDTH = 220;
        private final String text;
        private int lastWidth = -1;

        private WrappedTextBlock(String text) {
            this.text = text == null ? "" : text;
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredSize() {
            int width = resolveWrapWidth();
            return new Dimension(1, measureHeight(width));
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(0, lineHeight());
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        public void setBounds(int x, int y, int width, int height) {
            boolean widthChanged = width != lastWidth;
            super.setBounds(x, y, width, height);
            if (widthChanged) {
                lastWidth = width;
                revalidate();
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(getForeground());
                paintText(g2, Math.max(1, getWidth()));
            } finally {
                g2.dispose();
            }
        }

        private int resolveWrapWidth() {
            if (getWidth() > 0) {
                return getWidth();
            }
            Container parent = getParent();
            if (parent != null && parent.getWidth() > 0) {
                Insets insets = parent.getInsets();
                return Math.max(1, parent.getWidth() - insets.left - insets.right);
            }
            return FALLBACK_WRAP_WIDTH;
        }

        private int measureHeight(int width) {
            if (text.isBlank()) {
                return lineHeight();
            }
            Graphics2D g2 = graphicsForMeasurement();
            try {
                return Math.max(lineHeight(), measureText(g2, width));
            } finally {
                g2.dispose();
            }
        }

        private Graphics2D graphicsForMeasurement() {
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = image.createGraphics();
            g2.setFont(getFont());
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            return g2;
        }

        private int measureText(Graphics2D g2, int width) {
            int height = 0;
            FontMetrics metrics = g2.getFontMetrics(getFont());
            String[] paragraphs = text.split("\\R", -1);
            for (String paragraph : paragraphs) {
                if (paragraph.isEmpty()) {
                    height += metrics.getHeight();
                    continue;
                }
                LineBreakMeasurer measurer = createMeasurer(paragraph, g2);
                while (measurer.getPosition() < paragraph.length()) {
                    TextLayout layout = measurer.nextLayout(Math.max(1, width));
                    height += (int) Math.ceil(layout.getAscent() + layout.getDescent() + layout.getLeading());
                }
            }
            return height;
        }

        private void paintText(Graphics2D g2, int width) {
            float y = 0f;
            FontMetrics metrics = g2.getFontMetrics(getFont());
            String[] paragraphs = text.split("\\R", -1);
            for (String paragraph : paragraphs) {
                if (paragraph.isEmpty()) {
                    y += metrics.getHeight();
                    continue;
                }
                LineBreakMeasurer measurer = createMeasurer(paragraph, g2);
                while (measurer.getPosition() < paragraph.length()) {
                    TextLayout layout = measurer.nextLayout(Math.max(1, width));
                    y += layout.getAscent();
                    layout.draw(g2, 0, y);
                    y += layout.getDescent() + layout.getLeading();
                }
            }
        }

        private LineBreakMeasurer createMeasurer(String paragraph, Graphics2D g2) {
            AttributedString attributed = new AttributedString(paragraph);
            attributed.addAttribute(TextAttribute.FONT, getFont());
            return new LineBreakMeasurer(attributed.getIterator(), g2.getFontRenderContext());
        }

        private int lineHeight() {
            Font font = getFont();
            if (font == null) {
                return 16;
            }
            return getFontMetrics(font).getHeight();
        }
    }

    private static final class ViewportWidthPanel extends JPanel implements Scrollable {
        private ViewportWidthPanel(java.awt.LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 96;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            Container parent = getParent();
            return parent != null && getPreferredSize().height < parent.getHeight();
        }
    }
}
