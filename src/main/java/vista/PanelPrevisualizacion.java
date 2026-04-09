package vista;

import controlador.GestorMundos;
import controlador.GestorServidores;
import controlador.Utilidades;
import modelo.Server;
import modelo.World;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PanelPrevisualizacion extends JPanel {
    private static final String[] EASY_MC_IMAGE_RESOURCES = {
            "easymcimages/logo_64.png"
    };

    private final GestorServidores gestorServidores;
    private Server server = new Server();

    PanelPrevisualizacion(GestorServidores gestorServidores){
        this.gestorServidores = gestorServidores;
        this.server = gestorServidores.getServidorSeleccionado();
        this.setLayout(new BorderLayout());
        this.setOpaque(false);

        JPanel contenido = new JPanel(new BorderLayout());
        contenido.setOpaque(false);
        this.add(contenido, BorderLayout.CENTER);

        ImageIcon icono = server.getServerIconOrUseDefault();
        ImagenRedondaLabel iconoRedondo = new ImagenRedondaLabel(icono, 10, 96, 96);
        iconoRedondo.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        iconoRedondo.setToolTipText("Cambiar icono del servidor");
        iconoRedondo.setHoverOverlayEnabled(true);
        iconoRedondo.setHoverText("EDIT");
        iconoRedondo.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if(!SwingUtilities.isLeftMouseButton(e)) return;
                cambiarIconoServidor(gestorServidores, server, iconoRedondo);
            }
        });

        JPanel iconWrap = new JPanel(new GridBagLayout());
        iconWrap.setOpaque(false);
        iconWrap.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        iconWrap.add(iconoRedondo);
        contenido.add(iconWrap, BorderLayout.WEST);

        JPanel panelDatos = new JPanel();
        panelDatos.setOpaque(false);
        panelDatos.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 10));
        panelDatos.setLayout(new BoxLayout(panelDatos, BoxLayout.Y_AXIS));

        String nombre = server.getDisplayName();
        String motdRaw = Utilidades.leerMotdDesdeProperties(Path.of(server.getServerDir()));
        String motd = MotdRenderUtil.stripCodes(motdRaw);
        if(motd != null){
            motd = motd.replace("\r", "");
            String[] parts = motd.split("\\R", -1);
            motd = parts.length <= 2 ? motd : (parts[0] + "\n" + parts[1]);
        }
        String version = server.getVersion();

        JLabel nombreLabel = new JLabel(nombre);
        nombreLabel.setFont(nombreLabel.getFont().deriveFont(Font.BOLD, 18f));

        JTextField nombreField = new JTextField(nombre);
        nombreField.setFont(nombreLabel.getFont());
        javax.swing.border.Border borderNormal = BorderFactory.createEmptyBorder(2, 6, 2, 6);
        Font fontNormal = nombreField.getFont();
        nombreField.setBorder(borderNormal);
        nombreField.setOpaque(false);
        nombreField.setEditable(false);
        nombreField.setForeground(nombreLabel.getForeground());
        nombreField.setHorizontalAlignment(SwingConstants.LEFT);
        nombreField.putClientProperty("fullText", nombre == null ? "" : nombre);
        nombreField.setToolTipText(nombre);

        nombreField.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        nombreField.setToolTipText("Click para editar (Enter o fuera para guardar)");
        nombreField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if(nombreField.isEditable()) return;
                if(fontNormal != null){
                    java.util.Map<java.awt.font.TextAttribute, Object> attrs = new java.util.HashMap<>(fontNormal.getAttributes());
                    attrs.put(java.awt.font.TextAttribute.UNDERLINE, java.awt.font.TextAttribute.UNDERLINE_ON);
                    nombreField.setFont(fontNormal.deriveFont(attrs));
                }
                nombreField.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if(nombreField.isEditable()) return;
                nombreField.setOpaque(false);
                nombreField.setBorder(borderNormal);
                if(fontNormal != null){
                    nombreField.setFont(fontNormal);
                }
                nombreField.repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if(!SwingUtilities.isLeftMouseButton(e)) return;
                if(nombreField.isEditable()) return;
                nombreField.setEditable(true);
                nombreField.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
                if(fontNormal != null){
                    nombreField.setFont(fontNormal);
                }
                nombreField.setOpaque(false);
                nombreField.setBorder(borderNormal);
                nombreField.requestFocusInWindow();
                nombreField.selectAll();
            }
        });

        final JLabel[] motdLabelRef = new JLabel[1];

        Runnable commitNombre = () -> {
            if(!nombreField.isEditable()) return;
            String nuevo = nombreField.getText();
            nuevo = (nuevo == null) ? "" : nuevo.strip();
            if(!nuevo.isBlank()){
                server.setDisplayName(nuevo);
                nombreField.putClientProperty("fullText", nuevo);
                try{
                    gestorServidores.guardarServidor(server);
                } catch (RuntimeException ignored){
                }
            } else {
                String actual = server.getDisplayName();
                nombreField.setText(actual == null ? "" : actual);
                nombreField.putClientProperty("fullText", actual == null ? "" : actual);
            }
            nombreField.setEditable(false);
            nombreField.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            nombreField.setOpaque(false);
            nombreField.setBorder(borderNormal);
            if(fontNormal != null){
                nombreField.setFont(fontNormal);
            }
            aplicarEllipsisTextos(nombreField, motdLabelRef[0]);
        };
        nombreField.addActionListener(e -> commitNombre.run());
        nombreField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                commitNombre.run();
            }
        });

        String motdRawDisplay = (motdRaw == null) ? "" : motdRaw.replace("\r", "").replace('&', '\u00A7');
        String[] motdRawParts = motdRawDisplay.split("\\R", -1);
        motdRawDisplay = motdRawParts.length <= 2 ? motdRawDisplay : (motdRawParts[0] + "\n" + motdRawParts[1]);

        JLabel motdLabel = new JLabel(MotdRenderUtil.toHtml(motdRawDisplay));
        motdLabel.setFont(motdLabel.getFont().deriveFont(Font.PLAIN, 15f));
        motdLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        motdLabel.setHorizontalAlignment(SwingConstants.LEFT);
        motdLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        motdLabel.setToolTipText("Click para editar MOTD");
        motdLabel.putClientProperty("fullText", motd == null ? "" : motd);
        motdLabel.setToolTipText(motd);
        motdLabel.setForeground(AppTheme.getConsoleForeground());
        motdLabelRef[0] = motdLabel;

        Font motdFontNormal = motdLabel.getFont();
        motdLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if(motdFontNormal == null) return;
                java.util.Map<java.awt.font.TextAttribute, Object> attrs = new java.util.HashMap<>(motdFontNormal.getAttributes());
                attrs.put(java.awt.font.TextAttribute.UNDERLINE, java.awt.font.TextAttribute.UNDERLINE_ON);
                motdLabel.setFont(motdFontNormal.deriveFont(attrs));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if(motdFontNormal != null) motdLabel.setFont(motdFontNormal);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if(!SwingUtilities.isLeftMouseButton(e)) return;
                String base = Utilidades.leerMotdDesdeProperties(Path.of(server.getServerDir()));
                String nuevo = MotdEditorDialog.show(PanelPrevisualizacion.this, base);
                if(nuevo == null) return;
                Utilidades.escribirMotdEnProperties(Path.of(server.getServerDir()), nuevo);
                String limpio = MotdRenderUtil.stripCodes(nuevo);
                if(limpio != null){
                    limpio = limpio.replace("\r", "");
                    String[] parts = limpio.split("\\R", -1);
                    limpio = parts.length <= 2 ? limpio : (parts[0] + "\n" + parts[1]);
                }
                motdLabel.putClientProperty("fullText", limpio == null ? "" : limpio);
                String nuevoDisplay = (nuevo == null) ? "" : nuevo.replace("\r", "").replace('&', '\u00A7');
                String[] nuevoParts = nuevoDisplay.split("\\R", -1);
                nuevoDisplay = nuevoParts.length <= 2 ? nuevoDisplay : (nuevoParts[0] + "\n" + nuevoParts[1]);
                motdLabel.setText(MotdRenderUtil.toHtml(nuevoDisplay));
                aplicarEllipsisTextos(nombreField, motdLabel);
                try{
                    gestorServidores.guardarServidor(server);
                } catch (RuntimeException ignored){
                }
            }
        });

        JLabel versionLabel = new JLabel((version == null || version.isBlank()) ? "(sin version)" : ("Version: " + version));
        versionLabel.setFont(versionLabel.getFont().deriveFont(Font.PLAIN, 15f));
        versionLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        versionLabel.setHorizontalAlignment(SwingConstants.LEFT);

        nombreField.setAlignmentX(Component.LEFT_ALIGNMENT);
        motdLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        versionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel motdBox = new JPanel(new BorderLayout());
        motdBox.setOpaque(true);
        motdBox.setBackground(AppTheme.getConsoleBackground());
        motdBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.getConsoleOutlineColor(), 1, true),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        motdBox.add(motdLabel, BorderLayout.CENTER);
        motdBox.setAlignmentX(Component.LEFT_ALIGNMENT);

        panelDatos.add(nombreField);
        panelDatos.add(motdBox);
        panelDatos.add(versionLabel);
        panelDatos.add(Box.createVerticalGlue());

        JPanel panelDatosWrap = new JPanel(new BorderLayout());
        panelDatosWrap.setOpaque(false);
        panelDatosWrap.add(panelDatos, BorderLayout.NORTH);
        contenido.add(panelDatosWrap, BorderLayout.CENTER);

        Runnable ajustar = () -> aplicarEllipsisTextos(nombreField, motdLabel);
        SwingUtilities.invokeLater(ajustar);
        panelDatosWrap.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                ajustar.run();
            }
        });
    }

    private void cambiarIconoServidor(GestorServidores gestorServidores, Server server, ImagenRedondaLabel iconoRedondo){
        if(server == null) return;
        String dir = server.getServerDir();
        if(dir == null || dir.isBlank()) return;

        Path outPath = Path.of(dir).resolve("server-icon.png");
        try{
            ImageSelectionResult selection = seleccionarImagenServidor(server);
            if(selection == null || selection.image() == null) return;

            BufferedImage icon64 = selection.useDirectly()
                    ? selection.image()
                    : CropIconDialog.show(this, selection.image());
            if(icon64 == null) return;
            Files.createDirectories(outPath.getParent());
            ImageIO.write(icon64, "png", outPath.toFile());

            iconoRedondo.setImageIcon(new ImageIcon(icon64));
            iconoRedondo.revalidate();
            iconoRedondo.repaint();
            PanelPrevisualizacion.this.revalidate();
            PanelPrevisualizacion.this.repaint();

            try{
                gestorServidores.guardarServidor(server);
            } catch (RuntimeException ignored){
            }
        } catch (IOException ex){
            JOptionPane.showMessageDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "Error guardando server-icon.png: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private ImageSelectionResult seleccionarImagenServidor(Server server) throws IOException {
        List<EasyMcImageOption> easyMcImages = obtenerEasyMcImages();
        List<Server> servidores = gestorServidores != null && gestorServidores.getListaServidores() != null
                ? gestorServidores.getListaServidores()
                : List.of();
        Window owner = SwingUtilities.getWindowAncestor(this);
        return SelectorImagenServidorDialog.show(owner, servidores, server, easyMcImages, this::elegirImagenNativa);
    }

    private List<PreviewMundoOption> obtenerPreviewsMundosServidor(Server server) {
        if(server == null) return List.of();

        try{
            GestorMundos.sincronizarMundosServidor(server);
            World mundoActivo = GestorMundos.getMundoActivo(server);
            String mundoActivoNombre = mundoActivo != null ? mundoActivo.getWorldName() : null;
            List<PreviewMundoOption> previews = new ArrayList<>();
            for(World mundo : GestorMundos.listarMundos(server)){
                if(mundo == null || mundo.getWorldDir() == null || mundo.getWorldDir().isBlank()) continue;
                Path previewPath = Path.of(mundo.getWorldDir()).resolve("preview.png");
                if(!Files.isRegularFile(previewPath)) continue;
                boolean activo = mundoActivoNombre != null && mundoActivoNombre.equalsIgnoreCase(mundo.getWorldName());
                previews.add(new PreviewMundoOption(mundo.getWorldName(), previewPath, activo));
            }
            previews.sort((a, b) -> {
                if(a.activeWorld() != b.activeWorld()){
                    return a.activeWorld() ? -1 : 1;
                }
                return a.worldName().compareToIgnoreCase(b.worldName());
            });
            return previews;
        } catch (RuntimeException ex){
            return List.of();
        }
    }

    private File elegirImagenNativa(){
        Window owner = SwingUtilities.getWindowAncestor(this);
        Frame frameOwner = (owner instanceof Frame f) ? f : null;

        FileDialog dialog = new FileDialog(frameOwner, "Selecciona una imagen", FileDialog.LOAD);
        dialog.setMultipleMode(false);

        try{
            File pictures = Path.of(System.getProperty("user.home"), "Pictures").toFile();
            if(pictures.isDirectory()){
                dialog.setDirectory(pictures.getAbsolutePath());
            }
        } catch (RuntimeException ignored){
        }

        dialog.setFilenameFilter((dir, name) -> {
            if(name == null) return false;
            String n = name.toLowerCase();
            return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp") || n.endsWith(".bmp") || n.endsWith(".gif");
        });

        dialog.setVisible(true);
        String file = dialog.getFile();
        String dir = dialog.getDirectory();
        if(file == null || dir == null) return null;
        return new File(dir, file);
    }

    private record PreviewMundoOption(String worldName, Path previewPath, boolean activeWorld) {
        @Override
        public String toString() {
            return worldName == null ? "(sin nombre)" : worldName;
        }
    }
    private record EasyMcImageOption(String name, String resourcePath) {}
    private record ImageSelectionResult(BufferedImage image, boolean useDirectly) {}

    @FunctionalInterface
    private interface ImageFilePicker {
        File pick();
    }

    private List<EasyMcImageOption> obtenerEasyMcImages() {
        List<EasyMcImageOption> images = new ArrayList<>();
        for(String resourcePath : EASY_MC_IMAGE_RESOURCES){
            if(resourcePath == null || resourcePath.isBlank()) continue;
            if(getClass().getClassLoader().getResource(resourcePath) == null) continue;

            String fileName = resourcePath;
            int slash = fileName.lastIndexOf('/');
            if(slash >= 0 && slash + 1 < fileName.length()){
                fileName = fileName.substring(slash + 1);
            }
            images.add(new EasyMcImageOption(fileName, resourcePath));
        }
        return images;
    }

    private static final class SelectorImagenServidorDialog {
        private static final class ViewportFillPanel extends JPanel implements Scrollable {
            private ViewportFillPanel() {
            }

            @Override
            public Dimension getPreferredScrollableViewportSize() {
                return getPreferredSize();
            }

            @Override
            public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
                return 16;
            }

            @Override
            public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
                return orientation == SwingConstants.VERTICAL ? visibleRect.height - 32 : visibleRect.width - 32;
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

        private static ImageSelectionResult show(Window owner, List<Server> servers, Server selectedServer, List<EasyMcImageOption> easyMcImages, ImageFilePicker picker) throws IOException {
            JDialog dialog = new JDialog(owner, "Seleccionar imagen del servidor", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

            ImageSelectionResult[] result = new ImageSelectionResult[1];

            JPanel content = new JPanel(new BorderLayout(12, 12));
            content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
            content.setBackground(AppTheme.getPanelBackground());

            JLabel title = new JLabel("Selecciona una imagen");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

            JLabel subtitle = new JLabel("Usa una imagen interna, una preview de cualquier servidor o arrastra una imagen desde el ordenador.");
            subtitle.setForeground(AppTheme.getMutedForeground());

            JPanel header = new JPanel();
            header.setOpaque(false);
            header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
            header.add(title);
            header.add(Box.createVerticalStrut(4));
            header.add(subtitle);
            content.add(header, BorderLayout.NORTH);

            JPanel center = new JPanel(new BorderLayout(12, 12));
            center.setOpaque(false);
            center.add(crearZonaArrastre(dialog, result, picker), BorderLayout.NORTH);
            center.add(crearListadoImagenes(dialog, servers, selectedServer, easyMcImages, result), BorderLayout.CENTER);
            content.add(center, BorderLayout.CENTER);

            JButton cancel = new JButton("Cancelar");
            cancel.addActionListener(e -> {
                result[0] = null;
                dialog.dispose();
            });

            JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            footer.setOpaque(false);
            footer.add(cancel);
            content.add(footer, BorderLayout.SOUTH);

            dialog.setContentPane(content);
            dialog.setMinimumSize(new Dimension(760, 560));
            dialog.setSize(860, 620);
            dialog.setLocationRelativeTo(owner);
            dialog.setVisible(true);
            return result[0];
        }

        private static JComponent crearZonaArrastre(JDialog dialog, ImageSelectionResult[] result, ImageFilePicker picker) {
            Runnable abrirSelector = () -> {
                try{
                    BufferedImage image = cargarImagenElegida(picker);
                    if(image == null) return;
                    result[0] = new ImageSelectionResult(image, false);
                    dialog.dispose();
                } catch (IOException ex){
                    mostrarError(dialog, ex.getMessage());
                }
            };

            JPanel uploadPanel = new JPanel(new BorderLayout(0, 10));
            uploadPanel.setOpaque(true);
            uploadPanel.setBackground(AppTheme.getSurfaceBackground());
            uploadPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            uploadPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createDashedBorder(AppTheme.getMainAccent(), 1f, 6f, 3f, true),
                    BorderFactory.createEmptyBorder(28, 28, 28, 28)
            ));
            uploadPanel.setPreferredSize(new Dimension(100, 170));

            JLabel title = new JLabel("Arrastra una imagen aqui", SwingConstants.CENTER);
            title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

            JLabel subtitle = new JLabel("o seleccionala desde tu ordenador", SwingConstants.CENTER);
            subtitle.setForeground(AppTheme.getMutedForeground());

            JPanel textWrap = new JPanel(new GridBagLayout());
            textWrap.setOpaque(false);
            JPanel centeredText = new JPanel();
            centeredText.setOpaque(false);
            centeredText.setLayout(new BoxLayout(centeredText, BoxLayout.Y_AXIS));
            title.setAlignmentX(Component.CENTER_ALIGNMENT);
            subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
            centeredText.add(title);
            centeredText.add(Box.createVerticalStrut(6));
            centeredText.add(subtitle);

            textWrap.add(centeredText);

            uploadPanel.add(textWrap, BorderLayout.CENTER);
            installImageDropHandler(uploadPanel, dialog, result);
            uploadPanel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if(!SwingUtilities.isLeftMouseButton(e)) return;
                    abrirSelector.run();
                }
            });
            return uploadPanel;
        }

        private static JComponent crearListadoImagenes(JDialog dialog, List<Server> servers, Server selectedServer, List<EasyMcImageOption> easyMcImages, ImageSelectionResult[] result) {
            JPanel sections = new ViewportFillPanel();
            sections.setOpaque(false);
            sections.setLayout(new BoxLayout(sections, BoxLayout.Y_AXIS));

            sections.add(crearSeccionEasyMcImages(dialog, easyMcImages, result));
            sections.add(Box.createVerticalStrut(12));
            sections.add(crearSeccionPreviews(dialog, servers, selectedServer, result));

            JScrollPane scrollPane = new JScrollPane(sections);
            scrollPane.setBorder(null);
            scrollPane.getVerticalScrollBar().setUnitIncrement(16);
            scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scrollPane.setOpaque(false);
            scrollPane.getViewport().setOpaque(false);
            scrollPane.getViewport().setBackground(AppTheme.getPanelBackground());
            return scrollPane;
        }

        private static JComponent crearSeccionEasyMcImages(JDialog dialog, List<EasyMcImageOption> easyMcImages, ImageSelectionResult[] result) {
            JPanel wrap = new JPanel(new WrapLayout(FlowLayout.LEFT, 12, 12));
            wrap.setOpaque(false);

            if(easyMcImages == null || easyMcImages.isEmpty()){
                wrap.add(crearTarjetaVacia("No hay imagenes internas disponibles ahora mismo."));
            } else {
                for(EasyMcImageOption image : easyMcImages){
                    wrap.add(crearBotonEasyMcImage(dialog, image, result));
                }
            }

            JPanel section = new JPanel(new BorderLayout(0, 8));
            section.setOpaque(false);
            section.setBorder(BorderFactory.createTitledBorder("Easy MC Images"));
            section.add(wrap, BorderLayout.CENTER);
            return section;
        }

        private static JComponent crearSeccionPreviews(JDialog dialog, List<Server> servers, Server selectedServer, ImageSelectionResult[] result) {
            JPanel section = new JPanel(new BorderLayout(0, 6));
            section.setOpaque(false);
            section.setBorder(BorderFactory.createTitledBorder("Previews de mundos"));

            JComboBox<Server> serverCombo = new JComboBox<>((servers == null ? List.<Server>of() : servers).toArray(Server[]::new));
            serverCombo.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if(value instanceof Server server){
                        String name = server.getDisplayName();
                        if(name == null || name.isBlank()){
                            name = server.getServerDir() == null ? "(sin nombre)" : server.getServerDir();
                        }
                        setText(name);
                    }
                    return this;
                }
            });
            if(selectedServer != null){
                serverCombo.setSelectedItem(selectedServer);
            }
            JButton resetButton = new JButton("Reiniciar");

            JPanel wrap = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 8));
            wrap.setOpaque(false);
            Runnable recargarPreviews = () -> {
                wrap.removeAll();
                Object selected = serverCombo.getSelectedItem();
                List<PreviewMundoOption> previews = selected instanceof Server server
                        ? cargarPreviewsServidor(server)
                        : List.of();
                if(previews.isEmpty()){
                    wrap.add(crearTarjetaVacia("No hay previews generadas todavia para este servidor."));
                } else {
                    for(PreviewMundoOption preview : previews){
                        wrap.add(crearTarjetaPreview(dialog, preview, result));
                    }
                }
                wrap.revalidate();
                wrap.repaint();
            };
            serverCombo.addActionListener(e -> recargarPreviews.run());
            resetButton.addActionListener(e -> {
                if(selectedServer != null){
                    serverCombo.setSelectedItem(selectedServer);
                }
            });
            recargarPreviews.run();

            JPanel top = new JPanel(new BorderLayout(6, 0));
            top.setOpaque(false);
            top.add(serverCombo, BorderLayout.CENTER);
            top.add(resetButton, BorderLayout.EAST);
            section.add(top, BorderLayout.NORTH);
            section.add(wrap, BorderLayout.CENTER);
            return section;
        }

        private static JComponent crearTarjetaVacia(String texto) {
            JPanel empty = new JPanel(new BorderLayout());
            empty.setOpaque(true);
            empty.setBackground(AppTheme.getSurfaceBackground());
            empty.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(AppTheme.getSubtleBorderColor()),
                    BorderFactory.createEmptyBorder(18, 18, 18, 18)
            ));
            JLabel label = new JLabel(texto);
            label.setForeground(AppTheme.getMutedForeground());
            empty.add(label, BorderLayout.CENTER);
            return empty;
        }

        private static JComponent crearTarjetaPreview(JDialog dialog, PreviewMundoOption preview, ImageSelectionResult[] result) {
            JButton card = new JButton();
            card.setLayout(new BorderLayout(0, 6));
            card.setPreferredSize(new Dimension(198, 154));
            card.setFocusPainted(false);
            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            card.setHorizontalAlignment(SwingConstants.LEFT);
            card.setVerticalAlignment(SwingConstants.TOP);
            card.setBackground(AppTheme.getSurfaceBackground());
            card.setBorder(BorderFactory.createCompoundBorder(
                    AppTheme.createRoundedBorder(new Insets(8, 8, 8, 8), 1f),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)
            ));

            JLabel imageLabel = new JLabel("Cargando...", SwingConstants.CENTER);
            imageLabel.setPreferredSize(new Dimension(180, 110));
            imageLabel.setOpaque(true);
            imageLabel.setBackground(AppTheme.getPanelBackground());
            imageLabel.setBorder(BorderFactory.createLineBorder(AppTheme.getSubtleBorderColor()));
            cargarMiniaturaPreview(preview.previewPath(), imageLabel);

            JLabel nameLabel = new JLabel(preview.worldName());
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));

            JPanel textPanel = new JPanel();
            textPanel.setOpaque(false);
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
            textPanel.add(nameLabel);

            card.add(imageLabel, BorderLayout.CENTER);
            card.add(textPanel, BorderLayout.SOUTH);
            card.addActionListener(e -> {
                try{
                    BufferedImage image = ImageIO.read(preview.previewPath().toFile());
                    if(image == null){
                        mostrarError(dialog, "No se ha podido leer la preview seleccionada.");
                        return;
                    }
                    result[0] = new ImageSelectionResult(image, false);
                    dialog.dispose();
                } catch (IOException ex){
                    mostrarError(dialog, "No se ha podido leer la preview: " + ex.getMessage());
                }
            });
            return card;
        }

        private static JComponent crearBotonEasyMcImage(JDialog dialog, EasyMcImageOption imageOption, ImageSelectionResult[] result) {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(64, 64));
            button.setMinimumSize(new Dimension(64, 64));
            button.setMaximumSize(new Dimension(64, 64));
            button.setFocusPainted(false);
            button.setBorderPainted(false);
            button.setContentAreaFilled(false);
            button.setOpaque(false);
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.setToolTipText(imageOption.name());
            cargarMiniaturaEasyMcImage(imageOption.resourcePath(), button);
            button.addActionListener(e -> {
                try{
                    BufferedImage image = leerImagenRecurso(imageOption.resourcePath());
                    if(image == null){
                        mostrarError(dialog, "No se ha podido leer la imagen seleccionada.");
                        return;
                    }
                    result[0] = new ImageSelectionResult(image, true);
                    dialog.dispose();
                } catch (IOException ex){
                    mostrarError(dialog, "No se ha podido leer la imagen interna: " + ex.getMessage());
                }
            });
            return button;
        }

        private static void cargarMiniaturaPreview(Path previewPath, JLabel imageLabel) {
            SwingWorker<ImageIcon, Void> worker = new SwingWorker<>() {
                @Override
                protected ImageIcon doInBackground() throws Exception {
                    BufferedImage image = ImageIO.read(previewPath.toFile());
                    if(image == null) return null;
                    Dimension fit = ajustarDentro(image.getWidth(), image.getHeight(), 190, 120);
                    Image scaled = image.getScaledInstance(fit.width, fit.height, Image.SCALE_SMOOTH);
                    return new ImageIcon(scaled);
                }

                @Override
                protected void done() {
                    try{
                        ImageIcon icon = get();
                        if(icon != null){
                            imageLabel.setText(null);
                            imageLabel.setIcon(icon);
                        } else {
                            imageLabel.setText("Preview no valida");
                        }
                    } catch (Exception ex){
                        imageLabel.setText("No disponible");
                    }
                }
            };
            worker.execute();
        }

        private static void cargarMiniaturaEasyMcImage(String resourcePath, AbstractButton target) {
            SwingWorker<ImageIcon, Void> worker = new SwingWorker<>() {
                @Override
                protected ImageIcon doInBackground() throws Exception {
                    BufferedImage image = leerImagenRecurso(resourcePath);
                    return image == null ? null : new ImageIcon(image);
                }

                @Override
                protected void done() {
                    try{
                        ImageIcon icon = get();
                        if(icon != null){
                            target.setText(null);
                            target.setIcon(icon);
                        } else {
                            target.setText("!");
                        }
                    } catch (Exception ex){
                        target.setText("!");
                    }
                }
            };
            worker.execute();
        }

        private static List<PreviewMundoOption> cargarPreviewsServidor(Server server) {
            if(server == null) return List.of();
            try{
                GestorMundos.sincronizarMundosServidor(server);
                World mundoActivo = GestorMundos.getMundoActivo(server);
                String mundoActivoNombre = mundoActivo != null ? mundoActivo.getWorldName() : null;
                List<PreviewMundoOption> previews = new ArrayList<>();
                for(World mundo : GestorMundos.listarMundos(server)){
                    if(mundo == null || mundo.getWorldDir() == null || mundo.getWorldDir().isBlank()) continue;
                    Path previewPath = Path.of(mundo.getWorldDir()).resolve("preview.png");
                    if(!Files.isRegularFile(previewPath)) continue;
                    boolean activo = mundoActivoNombre != null && mundoActivoNombre.equalsIgnoreCase(mundo.getWorldName());
                    previews.add(new PreviewMundoOption(mundo.getWorldName(), previewPath, activo));
                }
                previews.sort((a, b) -> {
                    if(a.activeWorld() != b.activeWorld()){
                        return a.activeWorld() ? -1 : 1;
                    }
                    return a.worldName().compareToIgnoreCase(b.worldName());
                });
                return previews;
            } catch (RuntimeException ex){
                return List.of();
            }
        }

        private static Dimension ajustarDentro(int srcWidth, int srcHeight, int maxWidth, int maxHeight) {
            if(srcWidth <= 0 || srcHeight <= 0) {
                return new Dimension(Math.max(1, maxWidth), Math.max(1, maxHeight));
            }
            double scale = Math.min((double) maxWidth / srcWidth, (double) maxHeight / srcHeight);
            scale = Math.max(0.0d, Math.min(1.0d, scale));
            int width = Math.max(1, (int) Math.round(srcWidth * scale));
            int height = Math.max(1, (int) Math.round(srcHeight * scale));
            return new Dimension(width, height);
        }

        private static void installImageDropHandler(JComponent target, JDialog dialog, ImageSelectionResult[] result) {
            target.setTransferHandler(new TransferHandler() {
                @Override
                public boolean canImport(TransferSupport support) {
                    return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
                }

                @Override
                @SuppressWarnings("unchecked")
                public boolean importData(TransferSupport support) {
                    if(!canImport(support)) return false;
                    try{
                        Transferable transferable = support.getTransferable();
                        List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                        if(files == null || files.isEmpty()) return false;
                        BufferedImage image = ImageIO.read(files.get(0));
                        if(image == null){
                            mostrarError(dialog, "El archivo arrastrado no es una imagen compatible.");
                            return false;
                        }
                        result[0] = new ImageSelectionResult(image, false);
                        dialog.dispose();
                        return true;
                    } catch (Exception ex){
                        mostrarError(dialog, "No se ha podido abrir la imagen arrastrada.");
                        return false;
                    }
                }
            });
        }

        private static BufferedImage cargarImagenElegida(ImageFilePicker picker) throws IOException {
            if(picker == null) return null;
            File selected = picker.pick();
            if(selected == null || !selected.exists()) return null;
            BufferedImage image = ImageIO.read(selected);
            if(image == null){
                throw new IOException("No se ha podido leer la imagen seleccionada.");
            }
            return image;
        }

        private static BufferedImage leerImagenRecurso(String resourcePath) throws IOException {
            if(resourcePath == null || resourcePath.isBlank()) return null;
            try(var in = PanelPrevisualizacion.class.getClassLoader().getResourceAsStream(resourcePath)){
                if(in == null) return null;
                return ImageIO.read(in);
            }
        }

        private static void mostrarError(Component parent, String message) {
            JOptionPane.showMessageDialog(parent, message, "Imagen no valida", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static BufferedImage scaleSquare(BufferedImage croppedSquare, int size){
        if(croppedSquare == null) return null;
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g.drawImage(croppedSquare, 0, 0, size, size, null);
        g.dispose();
        return out;
    }

    private static String stripMinecraftCodes(String s){
        return MotdRenderUtil.stripCodes(s);
    }

    private static void aplicarEllipsisTextos(JTextField nombreField, JLabel motdLabel){
        if(nombreField == null || motdLabel == null) return;
        if(nombreField.isEditable()) return;

        Container parent = nombreField.getParent();
        if(parent == null) return;
        int maxWidth = parent.getWidth();
        if(maxWidth <= 0) return;
        maxWidth = Math.max(0, maxWidth - 16);

        Object fullNameObj = nombreField.getClientProperty("fullText");
        String fullName = fullNameObj == null ? "" : String.valueOf(fullNameObj);
        fullName = fullName.strip();

        FontMetrics fmName = nombreField.getFontMetrics(nombreField.getFont());
        nombreField.setText(ellipsizePx(fullName, fmName, maxWidth));
        nombreField.setToolTipText(fullName);

        Object fullMotdObj = motdLabel.getClientProperty("fullText");
        String fullMotd = fullMotdObj == null ? "" : String.valueOf(fullMotdObj);
        motdLabel.setToolTipText(fullMotd);
    }

    private static String ellipsizePx(String s, FontMetrics fm, int maxWidthPx){
        if(s == null) return "";
        if(maxWidthPx <= 0) return "";

        String dots = "...";
        if(fm.stringWidth(s) <= maxWidthPx) return s;
        if(fm.stringWidth(dots) >= maxWidthPx) return dots;

        int lo = 0;
        int hi = s.length();
        while(lo < hi){
            int mid = (lo + hi + 1) >>> 1;
            String candidate = s.substring(0, mid) + dots;
            if(fm.stringWidth(candidate) <= maxWidthPx) lo = mid;
            else hi = mid - 1;
        }
        return s.substring(0, lo) + dots;
    }
}
