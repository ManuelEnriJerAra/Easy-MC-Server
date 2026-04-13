/*
 * Fichero: CropIconDialog.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 *
 * Descripción (paso a paso, a nivel alto):
 * - Abre un JDialog modal para recortar una imagen (icono).
 * - Muestra la imagen grande con una selección cuadrada movible/redimensionable.
 * - A la derecha muestra una previsualización escalada a 64x64.
 * - Al confirmar, devuelve el recorte final (64x64) como BufferedImage; si cancelas devuelve null.
 */

package vista;

import com.formdev.flatlaf.extras.components.FlatButton;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

import static java.lang.Math.*;

public class CropIconDialog {
    public static BufferedImage show(Component parent, BufferedImage src) {
        /*
         * 1) Validación y creación del diálogo
         *    - Si no hay imagen de entrada, no se puede recortar.
         *    - El diálogo es modal (bloquea la ventana principal hasta cerrar).
         */
        if (src == null) return null;
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Recortar icono", Dialog.ModalityType.APPLICATION_MODAL);

        /*
         * 2) Panel de recorte + previsualización
         *    - CropPanel dibuja la imagen y gestiona la selección (rectángulo cuadrado).
         *    - preview enseña el resultado escalado a 64x64.
         */

        CropPanel cropPanel = new CropPanel(src);
        JLabel preview = new JLabel();
        preview.setPreferredSize(new Dimension(96, 96));
        preview.setBorder(BorderFactory.createTitledBorder("Previsualización"));

        /*
         * 3) Opción de calidad al escalar
         *    - Si está activado, el escalado usa interpolación “suave” (mejor calidad, menos pixelado).
         *    - Si está desactivado, usa “nearest neighbor” (más rápido y más pixel-art).
         */
        JCheckBox antialias = new JCheckBox("Aplicar Antialias");
        antialias.setSelected(true);


        /*
         * 4) Actualización de la previsualización (con “debounce” dentro de CropPanel)
         *    - Cada vez que cambia la selección, pedimos el recorte (cuadrado) y lo escalamos a 64x64.
         *    - Aquí la preview se mantiene sin antialias para que sea ligera y consistente.
         */

        Runnable updatePreview = () -> {
            BufferedImage square = cropPanel.getCroppedSquare();
            if (square == null) {
                preview.setIcon(null);
                return;
            }
            // En la previsualización muestro la imagen con antialias si así lo quiere el usuario
            BufferedImage scaled = scaleSquare(square, 64, antialias.isSelected());
            preview.setIcon(new ImageIcon(scaled));
        };
        cropPanel.setOnSelectionChanged(() -> SwingUtilities.invokeLater(updatePreview));
        updatePreview.run();
        antialias.addActionListener(e -> updatePreview.run());
        // Nota: el checkbox solo afecta al resultado final, no a la previsualización.

        /*
         * 5) Botones y resultado
         *    - OK: toma el recorte actual y lo escala a 64x64 (con o sin antialias según el checkbox).
         *    - Cancelar / cerrar ventana: devuelve null.
         */
        JButton ok = new FlatButton();
        ok.setText("Usar recorte");
        JButton cancel = new FlatButton();
        cancel.setText("Cancelar");

        final BufferedImage[] result = new BufferedImage[1];

        ok.addActionListener(e -> {
            BufferedImage square = cropPanel.getCroppedSquare();
            result[0] = (square == null) ? null : scaleSquare(square, 64, antialias.isSelected());
            dialog.dispose();
        });

        cancel.addActionListener(e -> {
            result[0] = null;
            dialog.dispose();
        });

        /*
         * 6) Layout (disposición en pantalla)
         *    - Centro: zona grande para recortar.
         *    - Derecha: preview + checkbox.
         *    - Abajo: botones OK/Cancelar.
         */
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.add(preview);
        right.add(Box.createVerticalStrut(8));
        right.add(antialias);
        right.add(Box.createVerticalStrut(8));
        right.add(Box.createVerticalGlue());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(ok);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(cropPanel, BorderLayout.CENTER);
        content.add(right, BorderLayout.EAST);
        content.add(buttons, BorderLayout.SOUTH);

        /*
         * 7) Configuración final del diálogo
         *    - Tamaño mínimo y centrado relativo al componente padre.
         *    - Si el usuario cierra la ventana con la X, se interpreta como cancelar (null).
         */
        dialog.setContentPane(content);
        dialog.setMinimumSize(new Dimension(760, 520));
        dialog.setLocationRelativeTo(parent);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                result[0] = null;
            }
        });

        dialog.setVisible(true);
        return result[0];
    }

    /*
     * Escala una imagen cuadrada a `size` x `size`.
     * - Si `antialias` es true: usa interpolación bicúbica (más suave).
     * - Si `antialias` es false: usa nearest-neighbor (más “pixelado”).
     */
    private static BufferedImage scaleSquare(BufferedImage src, int size, boolean antialias) {
        if (src == null) return null;
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        if (antialias) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        } else {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
        }
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        return out;
    }

    private static class CropPanel extends JComponent {
        private final BufferedImage image;
        /*
         * selection y anchor están en coordenadas de la imagen (no del componente).
         * Esto simplifica recortar con getSubimage() y evita errores al hacer zoom/resize del panel.
         */
        private Rectangle selection;
        private Point anchor;
        private DragMode dragMode = DragMode.NONE;
        private Rectangle dragStartSelection;
        private Runnable onSelectionChanged;
        private final Timer debounce;

        CropPanel(BufferedImage image) {
            this.image = image;
            setOpaque(true);
            setBackground(AppTheme.getCropBackground());
            setPreferredSize(new Dimension(620, 460));

            // Selección inicial: cuadrado centrado que cabe en la imagen.
            int side = min(image.getWidth(), image.getHeight());
            int x = (image.getWidth() - side) / 2;
            int y = (image.getHeight() - side) / 2;
            selection = new Rectangle(x, y, side, side);

            /*
             * “Debounce” (anti-spam):
             * mientras arrastras el ratón se generan muchos eventos; en vez de recalcular la preview en cada uno,
             * esperamos ~100ms sin movimiento para lanzar una sola actualización.
             */

            debounce = new Timer(100, e -> {
                ((Timer) e.getSource()).stop();
                if (onSelectionChanged != null) onSelectionChanged.run();
            });
            debounce.setRepeats(false);

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    // Convertimos el punto del ratón (coordenadas del componente) a coordenadas de la imagen.
                    Point punto = viewToImage(e.getPoint());
                    if (punto == null) return;
                    anchor = punto;
                    dragStartSelection = (selection == null) ? null : new Rectangle(selection);

                    /*
                     * Decidimos qué “modo” de arrastre usar:
                     * - Si el clic cae sobre un handle/borde: redimensionar (RESIZE_...).
                     * - Si cae dentro: mover (MOVE).
                     * - Si cae fuera: crear una selección nueva (CREATE).
                     */

                    Hit hit = hitTest(e.getPoint());
                    if(hit != null){
                        dragMode = hit.mode;
                    } else {
                        dragMode = DragMode.CREATE;
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (anchor == null) return;
                    Point p = viewToImage(e.getPoint());
                    if (p == null) return;

                    // Actualizamos la selección según el modo de arrastre actual (crear / mover / redimensionar).
                    selection = switch (dragMode) {
                        case NONE -> selection;
                        case CREATE -> createFromAnchor(anchor, p);
                        case MOVE -> moveSelection(dragStartSelection, anchor, p);
                        case RESIZE_N, RESIZE_S, RESIZE_E, RESIZE_W,
                             RESIZE_NE, RESIZE_NW, RESIZE_SE, RESIZE_SW -> resizeSelection(dragStartSelection, dragMode, p);
                    };
                    if (selection != null) selection = constrainSelection(selection, dragMode, dragStartSelection);
                    repaint();
                    requestPreviewUpdate();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    // Terminamos el gesto: limpiamos estado temporal.
                    anchor = null;
                    dragMode = DragMode.NONE;
                    dragStartSelection = null;
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    // Cambiamos el cursor para indicar al usuario si puede mover o redimensionar.
                    Hit hit = hitTest(e.getPoint());
                    if(hit == null){
                        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                        return;
                    }
                    setCursor(Cursor.getPredefinedCursor(hit.cursor));
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        void setOnSelectionChanged(Runnable r) {
            this.onSelectionChanged = r;
        }

        BufferedImage getCroppedSquare() {
            // Devuelve el sub-rectángulo cuadrado dentro de la imagen original (sin escalar).
            if (selection == null) return null;
            Rectangle r = clampToImage(selection);
            if (r.width <= 0 || r.height <= 0) return null;
            return image.getSubimage(r.x, r.y, r.width, r.height);
        }

        private void requestPreviewUpdate() {
            debounce.restart();
        }

        private int clampInt(int value, int minValue, int maxValue) {
            if (maxValue < minValue) return minValue;
            return max(minValue, min(maxValue, value));
        }

        private Rectangle constrainSelection(Rectangle proposed, DragMode mode, Rectangle startSelection) {
            if (proposed == null) return null;

            int imgW = image.getWidth();
            int imgH = image.getHeight();
            if (imgW <= 0 || imgH <= 0) return null;

            int desiredSize = max(1, min(proposed.width, proposed.height));

            // CREATE no depende del "start": intentamos respetar (x,y) y reducimos size si toca bordes.
            if (mode == DragMode.CREATE) {
                int x = clampInt(proposed.x, 0, imgW - 1);
                int y = clampInt(proposed.y, 0, imgH - 1);
                int maxSize = min(imgW - x, imgH - y);
                int size = clampInt(desiredSize, 1, maxSize);
                return new Rectangle(x, y, size, size);
            }

            // Para MOVE/RESIZE necesitamos un rectángulo inicial para fijar bordes/esquinas.
            if (startSelection == null) return clampToImage(proposed);

            int startSize = max(1, min(startSelection.width, startSelection.height));
            startSize = min(startSize, min(imgW, imgH));

            int startX = startSelection.x;
            int startY = startSelection.y;
            int centerX = startX + startSize / 2;
            int centerY = startY + startSize / 2;

            return switch (mode) {
                case MOVE -> {
                    // Al mover, nunca se cambia el tamaño: solo x/y para que el cuadrado quepa.
                    int x = clampInt(proposed.x, 0, imgW - startSize);
                    int y = clampInt(proposed.y, 0, imgH - startSize);
                    yield new Rectangle(x, y, startSize, startSize);
                }

                // Lados: fijan un borde del cuadrado y ajustan el otro.
                case RESIZE_E -> {
                    int x = startX;
                    int maxSize = min(imgW - x, imgH);
                    int size = clampInt(desiredSize, 1, maxSize);
                    int y = clampInt(centerY - size / 2, 0, imgH - size);
                    yield new Rectangle(x, y, size, size);
                }
                case RESIZE_W -> {
                    int fixedRight = startX + startSize;
                    int maxSize = min(fixedRight, imgH);
                    int size = clampInt(desiredSize, 1, maxSize);
                    int x = fixedRight - size;
                    int y = clampInt(centerY - size / 2, 0, imgH - size);
                    yield new Rectangle(x, y, size, size);
                }
                case RESIZE_S -> {
                    int y = startY;
                    int maxSize = min(imgH - y, imgW);
                    int size = clampInt(desiredSize, 1, maxSize);
                    int x = clampInt(centerX - size / 2, 0, imgW - size);
                    yield new Rectangle(x, y, size, size);
                }
                case RESIZE_N -> {
                    int fixedBottom = startY + startSize;
                    int maxSize = min(fixedBottom, imgW);
                    int size = clampInt(desiredSize, 1, maxSize);
                    int y = fixedBottom - size;
                    int x = clampInt(centerX - size / 2, 0, imgW - size);
                    yield new Rectangle(x, y, size, size);
                }

                // Esquinas: fijan la esquina opuesta del cuadrado.
                case RESIZE_NW -> {
                    int fixedX = startX + startSize;
                    int fixedY = startY + startSize;
                    int maxSize = min(fixedX, fixedY);
                    int size = clampInt(desiredSize, 1, maxSize);
                    yield new Rectangle(fixedX - size, fixedY - size, size, size);
                }
                case RESIZE_NE -> {
                    int fixedX = startX;
                    int fixedY = startY + startSize;
                    int maxSize = min(imgW - fixedX, fixedY);
                    int size = clampInt(desiredSize, 1, maxSize);
                    yield new Rectangle(fixedX, fixedY - size, size, size);
                }
                case RESIZE_SW -> {
                    int fixedX = startX + startSize;
                    int fixedY = startY;
                    int maxSize = min(fixedX, imgH - fixedY);
                    int size = clampInt(desiredSize, 1, maxSize);
                    yield new Rectangle(fixedX - size, fixedY, size, size);
                }
                case RESIZE_SE -> {
                    int fixedX = startX;
                    int fixedY = startY;
                    int maxSize = min(imgW - fixedX, imgH - fixedY);
                    int size = clampInt(desiredSize, 1, maxSize);
                    yield new Rectangle(fixedX, fixedY, size, size);
                }

                default -> clampToImage(proposed);
            };
        }

        private Rectangle clampToImage(Rectangle r) {
            /*
             * Mantiene la selección dentro de los límites de la imagen y fuerza que sea cuadrada.
             * Importante: intenta preservar el tamaño (siempre que quepa), en vez de “encoger” al tocar bordes.
             */
            int imgW = image.getWidth();
            int imgH = image.getHeight();

            int size = max(1, min(r.width, r.height));
            size = min(size, min(imgW, imgH));

            int x = clampInt(r.x, 0, imgW - size);
            int y = clampInt(r.y, 0, imgH - size);
            return new Rectangle(x, y, size, size);
        }

        private Rectangle createFromAnchor(Point puntoA, Point puntoP){
            // Crea un cuadrado usando como tamaño el mayor desplazamiento (horizontal o vertical).
            int dx = puntoP.x - puntoA.x;
            int dy = puntoP.y - puntoA.y;
            int size = max(abs(dx), abs(dy));
            if (size < 2) size = 2;
            int x = (dx >= 0) ? puntoA.x : puntoA.x - size;
            int y = (dy >= 0) ? puntoA.y : puntoA.y - size;
            return new Rectangle(x, y, size, size);
        }

        private Rectangle moveSelection(Rectangle start, Point a, Point p){
            // Desplaza la selección sin cambiar su tamaño.
            if(start == null) return selection;
            int dx = p.x - a.x;
            int dy = p.y - a.y;
            return new Rectangle(start.x + dx, start.y + dy, start.width, start.height);
        }

        private Rectangle resizeSelection(Rectangle start, DragMode mode, Point p){
            if(start == null) return selection;
            Rectangle r = new Rectangle(start);
            int size = r.width;

            int cx = r.x + size / 2;
            int cy = r.y + size / 2;

            /*
             * Redimensionado manteniendo cuadrado:
             * - Si arrastras un lado, se actualiza el size y se recentra en el eje perpendicular.
             * - Si arrastras una esquina, el size sale del mayor desplazamiento.
             */
            switch (mode){
                case RESIZE_E -> {
                    int newSize = max(2, p.x - r.x);
                    r.width = r.height = newSize;
                    r.y = cy - newSize / 2;
                }
                case RESIZE_W -> {
                    int newSize = max(2, (r.x + size) - p.x);
                    r.x = (r.x + size) - newSize;
                    r.width = r.height = newSize;
                    r.y = cy - newSize / 2;
                }
                case RESIZE_S -> {
                    int newSize = max(2, p.y - r.y);
                    r.width = r.height = newSize;
                    r.x = cx - newSize / 2;
                }
                case RESIZE_N -> {
                    int newSize = max(2, (r.y + size) - p.y);
                    r.y = (r.y + size) - newSize;
                    r.width = r.height = newSize;
                    r.x = cx - newSize / 2;
                }
                case RESIZE_NW -> {
                    int fx = r.x + size;
                    int fy = r.y + size;
                    int newSize = max(2, max(fx - p.x, fy - p.y));
                    r.x = fx - newSize;
                    r.y = fy - newSize;
                    r.width = r.height = newSize;
                }
                case RESIZE_NE -> {
                    int fx = r.x;
                    int fy = r.y + size;
                    int newSize = max(2, max(p.x - fx, fy - p.y));
                    r.x = fx;
                    r.y = fy - newSize;
                    r.width = r.height = newSize;
                }
                case RESIZE_SW -> {
                    int fx = r.x + size;
                    int fy = r.y;
                    int newSize = max(2, max(fx - p.x, p.y - fy));
                    r.x = fx - newSize;
                    r.y = fy;
                    r.width = r.height = newSize;
                }
                case RESIZE_SE -> {
                    int fx = r.x;
                    int fy = r.y;
                    int newSize = max(2, max(p.x - fx, p.y - fy));
                    r.x = fx;
                    r.y = fy;
                    r.width = r.height = newSize;
                }
                default -> {
                }
            }
            return r;
        }

        private Point viewToImage(Point p) {
            // Conversión de coordenadas: componente (vista) -> imagen.
            Transform t = getTransform();
            if (t == null) return null;
            int ix = (int) Math.round((p.x - t.offsetX) / t.scale);
            int iy = (int) Math.round((p.y - t.offsetY) / t.scale);
            ix = max(0, min(image.getWidth() - 1, ix));
            iy = max(0, min(image.getHeight() - 1, iy));
            return new Point(ix, iy);
        }

        private Transform getTransform() {
            // Calcula cómo se dibuja la imagen dentro del panel (scale + offsets para centrar).
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return null;
            double sx = (double) w / image.getWidth();
            double sy = (double) h / image.getHeight();
            double scale = min(sx, sy);
            int drawW = (int) Math.round(image.getWidth() * scale);
            int drawH = (int) Math.round(image.getHeight() * scale);
            int ox = (w - drawW) / 2;
            int oy = (h - drawH) / 2;
            return new Transform(scale, ox, oy, drawW, drawH);
        }

        private Hit hitTest(Point viewPoint){
            /*
             * Detecta si el ratón está:
             * - sobre una esquina (RESIZE_NW/NE/SW/SE),
             * - sobre un borde (RESIZE_N/S/E/W),
             * - o dentro del cuadrado (MOVE).
             * Devuelve también el cursor apropiado.
             */
            if(selection == null) return null;
            Transform t = getTransform();
            if(t == null) return null;

            Rectangle s = clampToImage(selection);
            int sx = t.offsetX + (int) round(s.x * t.scale);
            int sy = t.offsetY + (int) round(s.y * t.scale);
            int sw = (int) round(s.width * t.scale);
            int sh = (int) round(s.height * t.scale);

            Rectangle box = new Rectangle(sx, sy, sw, sh);
            int tol = 7;

            Rectangle nw = new Rectangle(sx - tol, sy - tol, tol * 2, tol * 2);
            Rectangle ne = new Rectangle(sx + sw - tol, sy - tol, tol * 2, tol * 2);
            Rectangle swr = new Rectangle(sx - tol, sy + sh - tol, tol * 2, tol * 2);
            Rectangle se = new Rectangle(sx + sw - tol, sy + sh - tol, tol * 2, tol * 2);

            Rectangle n = new Rectangle(sx + tol, sy - tol, sw - tol * 2, tol * 2);
            Rectangle sR = new Rectangle(sx + tol, sy + sh - tol, sw - tol * 2, tol * 2);
            Rectangle w = new Rectangle(sx - tol, sy + tol, tol * 2, sh - tol * 2);
            Rectangle e = new Rectangle(sx + sw - tol, sy + tol, tol * 2, sh - tol * 2);

            if(nw.contains(viewPoint)) return new Hit(DragMode.RESIZE_NW, Cursor.NW_RESIZE_CURSOR);
            if(ne.contains(viewPoint)) return new Hit(DragMode.RESIZE_NE, Cursor.NE_RESIZE_CURSOR);
            if(swr.contains(viewPoint)) return new Hit(DragMode.RESIZE_SW, Cursor.SW_RESIZE_CURSOR);
            if(se.contains(viewPoint)) return new Hit(DragMode.RESIZE_SE, Cursor.SE_RESIZE_CURSOR);

            if(n.contains(viewPoint)) return new Hit(DragMode.RESIZE_N, Cursor.N_RESIZE_CURSOR);
            if(sR.contains(viewPoint)) return new Hit(DragMode.RESIZE_S, Cursor.S_RESIZE_CURSOR);
            if(w.contains(viewPoint)) return new Hit(DragMode.RESIZE_W, Cursor.W_RESIZE_CURSOR);
            if(e.contains(viewPoint)) return new Hit(DragMode.RESIZE_E, Cursor.E_RESIZE_CURSOR);

            if(box.contains(viewPoint)) return new Hit(DragMode.MOVE, Cursor.MOVE_CURSOR);
            return null;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Transform t = getTransform();
            if (t == null) {
                g2.dispose();
                return;
            }

            // Fondo
            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());

            // Imagen
            g2.drawImage(image, t.offsetX, t.offsetY, t.drawW, t.drawH, null);

            // Overlay + selección: oscurece fuera del recorte y dibuja borde/handles.
            if (selection != null) {
                Rectangle s = clampToImage(selection);
                int sx = t.offsetX + (int) Math.round(s.x * t.scale);
                int sy = t.offsetY + (int) Math.round(s.y * t.scale);
                int sw = (int) Math.round(s.width * t.scale);
                int sh = (int) Math.round(s.height * t.scale);

                // Oscurecemos todo EXCEPTO el recorte (sin usar AlphaComposite.Clear para evitar artefactos)
                g2.setColor(AppTheme.getCropOverlayColor());
                // arriba
                g2.fillRect(t.offsetX, t.offsetY, t.drawW, max(0, sy - t.offsetY));
                // abajo
                int bottomY = sy + sh;
                g2.fillRect(t.offsetX, bottomY, t.drawW, max(0, (t.offsetY + t.drawH) - bottomY));
                // izquierda
                g2.fillRect(t.offsetX, sy, max(0, sx - t.offsetX), sh);
                // derecha
                int rightX = sx + sw;
                g2.fillRect(rightX, sy, max(0, (t.offsetX + t.drawW) - rightX), sh);

                g2.setColor(AppTheme.getCropSelectionBorderColor());
                g2.setStroke(new BasicStroke(2f));
                g2.drawRect(sx, sy, sw, sh);

                // Handles
                int hs = 8;
                g2.setColor(AppTheme.getCropHandleColor());
                g2.fillRect(sx - hs/2, sy - hs/2, hs, hs);
                g2.fillRect(sx + sw - hs/2, sy - hs/2, hs, hs);
                g2.fillRect(sx - hs/2, sy + sh - hs/2, hs, hs);
                g2.fillRect(sx + sw - hs/2, sy + sh - hs/2, hs, hs);
                g2.fillRect(sx + sw/2 - hs/2, sy - hs/2, hs, hs);
                g2.fillRect(sx + sw/2 - hs/2, sy + sh - hs/2, hs, hs);
                g2.fillRect(sx - hs/2, sy + sh/2 - hs/2, hs, hs);
                g2.fillRect(sx + sw - hs/2, sy + sh/2 - hs/2, hs, hs);
            }

            g2.dispose();
        }

        private enum DragMode {
            NONE,
            CREATE,
            MOVE,
            RESIZE_N, RESIZE_S, RESIZE_E, RESIZE_W,
            RESIZE_NE, RESIZE_NW, RESIZE_SE, RESIZE_SW
        }

        private static class Hit {
            final DragMode mode;
            final int cursor;
            Hit(DragMode mode, int cursor) {
                this.mode = mode;
                this.cursor = cursor;
            }
        }

        private static class Transform {
            final double scale;
            final int offsetX;
            final int offsetY;
            final int drawW;
            final int drawH;

            Transform(double scale, int offsetX, int offsetY, int drawW, int drawH) {
                this.scale = scale;
                this.offsetX = offsetX;
                this.offsetY = offsetY;
                this.drawW = drawW;
                this.drawH = drawH;
            }
        }
    }
}
