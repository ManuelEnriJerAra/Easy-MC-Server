package vista;

import controlador.GestorServidores;
import controlador.Utilidades;
import modelo.Server;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

public class PanelPrevisualizacion extends JPanel {
    Server server = new Server();
    PanelPrevisualizacion(GestorServidores gestorServidores){
        this.server = gestorServidores.getServidorSeleccionado();
        this.setLayout(new BorderLayout());
        this.setOpaque(false);
        // Imagen a la izquierda
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
        // En BorderLayout.WEST el componente se estira en altura; lo envolvemos para respetar 96x96.
        JPanel iconWrap = new JPanel(new GridBagLayout());
        iconWrap.setOpaque(false);
        iconWrap.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        iconWrap.add(iconoRedondo);
        this.add(iconWrap, BorderLayout.WEST);

        // Texto a la derecha
        JPanel panelDatos = new JPanel();
        panelDatos.setOpaque(false);
        panelDatos.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        panelDatos.setLayout(new BoxLayout(panelDatos, BoxLayout.Y_AXIS));

        String nombre = server.getDisplayName();
        String motdRaw = Utilidades.leerMotdDesdeProperties(Path.of(server.getServerDir()));
        String motd = MotdRenderUtil.stripCodes(motdRaw);
        if(motd != null){
            motd = motd.replace("\r", "");
            // solo mostramos 2 líneas como Minecraft
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

        // Edición inline: click para editar, Enter o perder foco para guardar
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
                // Si queda vacío, revertimos a lo anterior
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
            }
        });
        JLabel versionLabel = new JLabel((version == null || version.isBlank()) ? "(sin versión)" : ("Versión: " + version));
        versionLabel.setFont(versionLabel.getFont().deriveFont(Font.PLAIN, 15f));
        versionLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        versionLabel.setHorizontalAlignment(SwingConstants.LEFT);

        nombreField.setAlignmentX(Component.LEFT_ALIGNMENT);
        motdLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        versionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // MOTD en recuadro oscuro (2 líneas)
        JPanel motdBox = new JPanel(new BorderLayout());
        motdBox.setOpaque(true);
        motdBox.setBackground(AppTheme.getConsoleBackground());
        motdBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.getConsoleOutlineColor(), 1, true),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        motdBox.add(motdLabel, BorderLayout.CENTER);
        motdBox.setAlignmentX(Component.LEFT_ALIGNMENT);

        // mostramos el campo (parece label hasta que se edita)
        panelDatos.add(nombreField);
        panelDatos.add(motdBox);
        panelDatos.add(versionLabel);
        panelDatos.add(Box.createVerticalGlue());

        // añadimos el texto al panel de previsualización
        JPanel panelDatosWrap = new JPanel(new BorderLayout());
        panelDatosWrap.setOpaque(false);
        panelDatosWrap.add(panelDatos, BorderLayout.NORTH);
        this.add(panelDatosWrap, BorderLayout.CENTER);

        // Ellipsize para que nombre/MOTD no rompan layout
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

        // File chooser nativo (Windows/Linux según toolkit): más rápido que generar thumbnails en Swing
        File selected = elegirImagenNativa();
        if(selected == null || !selected.exists()) return;

        Path outPath = Path.of(dir).resolve("server-icon.png");
        try{
            BufferedImage src = ImageIO.read(selected);
            if(src == null){
                JOptionPane.showMessageDialog(
                        SwingUtilities.getWindowAncestor(this),
                        "No se ha podido leer la imagen seleccionada.",
                        "Imagen no v\u00e1lida",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            BufferedImage icon64 = CropIconDialog.show(this, src);
            if(icon64 == null) return;
            Files.createDirectories(outPath.getParent());
            ImageIO.write(icon64, "png", outPath.toFile());

            // Usamos el BufferedImage directamente para evitar caché por ruta/ToolKit
            iconoRedondo.setImageIcon(new ImageIcon(icon64));
            iconoRedondo.revalidate();
            iconoRedondo.repaint();
            PanelPrevisualizacion.this.revalidate();
            PanelPrevisualizacion.this.repaint();

            // Forzamos refresco de lista/paneles (aunque el JSON no cambie)
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

        // Si el usuario está editando, no truncamos
        if(nombreField.isEditable()) return;

        Container parent = nombreField.getParent();
        if(parent == null) return;
        int maxWidth = parent.getWidth();
        if(maxWidth <= 0) return;
        maxWidth = Math.max(0, maxWidth - 16); // margen pequeño

        Object fullNameObj = nombreField.getClientProperty("fullText");
        String fullName = fullNameObj == null ? "" : String.valueOf(fullNameObj);
        fullName = fullName.strip();

        FontMetrics fmName = nombreField.getFontMetrics(nombreField.getFont());
        nombreField.setText(ellipsizePx(fullName, fmName, maxWidth));
        nombreField.setToolTipText(fullName);

        // MOTD: se renderiza en HTML con colores; mantenemos el tooltip en texto plano pero no truncamos el HTML aquí.
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
