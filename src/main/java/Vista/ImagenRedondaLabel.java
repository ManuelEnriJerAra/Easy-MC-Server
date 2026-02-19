package Vista;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

public class ImagenRedondaLabel extends JComponent {

    private Image imagen;
    private final int radio;
    private boolean hoverOverlayEnabled = false;
    private String hoverText = null;
    private boolean hovered = false;

    private BufferedImage cachedRounded;
    private int cachedW = -1;
    private int cachedH = -1;
    private Image cachedSource;

    public ImagenRedondaLabel(ImageIcon icono, int radio, int ancho, int alto) {
        this.imagen = icono == null ? null : icono.getImage();
        this.radio = radio;

        setPreferredSize(new Dimension(ancho, alto));
        setMinimumSize(new Dimension(ancho, alto));
        setMaximumSize(new Dimension(ancho, alto));

        // Transparente por defecto: si la imagen tiene alpha, no se "rellena" con un gris de fondo.
        setOpaque(false);
        setBackground(new Color(0, 0, 0, 0));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = false;
                repaint();
            }
        });
    }

    public void setImageIcon(ImageIcon icono){
        this.imagen = icono == null ? null : icono.getImage();
        invalidateCache();
        repaint();
    }

    public void setHoverOverlayEnabled(boolean enabled){
        this.hoverOverlayEnabled = enabled;
        repaint();
    }

    public void setHoverText(String text){
        this.hoverText = text;
        repaint();
    }

    private void invalidateCache() {
        cachedRounded = null;
        cachedW = -1;
        cachedH = -1;
        cachedSource = null;
    }

    private BufferedImage getOrCreateRoundedImage(int w, int h) {
        if (w <= 0 || h <= 0 || imagen == null) return null;
        if (cachedRounded != null && w == cachedW && h == cachedH && cachedSource == imagen) {
            return cachedRounded;
        }

        cachedW = w;
        cachedH = h;
        cachedSource = imagen;

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D ig = out.createGraphics();
        try {
            ig.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ig.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            ig.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // Máscara antialias (el "clip" de Java2D puede verse dentado; con máscara suele quedar más suave).
            ig.setComposite(AlphaComposite.Src);
            ig.setColor(Color.WHITE);
            ig.fill(new RoundRectangle2D.Float(0, 0, w, h, radio * 2f, radio * 2f));

            // Pinta la imagen "dentro" de la máscara.
            ig.setComposite(AlphaComposite.SrcIn);
            ig.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            ig.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            ig.drawImage(imagen, 0, 0, w, h, null);
        } finally {
            ig.dispose();
        }

        cachedRounded = out;
        return cachedRounded;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int w = getWidth();
        int h = getHeight();

        // Sin rellenar fondo: conserva transparencia real

        // Imagen
        BufferedImage rounded = getOrCreateRoundedImage(w, h);
        if (rounded != null) g2.drawImage(rounded, 0, 0, null);

        if(hoverOverlayEnabled && hovered){
            g2.setColor(new Color(0, 0, 0, 90));
            g2.fillRoundRect(0, 0, w, h, radio * 2, radio * 2);

            if(hoverText != null && !hoverText.isBlank()){
                g2.setColor(new Color(255, 255, 255, 230));
                Font base = getFont();
                if(base == null) base = UIManager.getFont("Label.font");
                if(base == null) base = new Font("Dialog", Font.PLAIN, 12);
                Font f = base.deriveFont(Font.PLAIN, Math.max(18f, Math.min(w, h) * 0.35f));
                g2.setFont(f);
                FontMetrics fm = g2.getFontMetrics();
                int tw = fm.stringWidth(hoverText);
                int th = fm.getAscent();
                int x = (w - tw) / 2;
                int y = (h + th) / 2 - fm.getDescent();
                g2.drawString(hoverText, x, y);
            }
        }

        g2.dispose();
    }
}
