package Vista;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class ImagenRedondaLabel extends JComponent {

    private final Image imagen;
    private final int radio;

    public ImagenRedondaLabel(ImageIcon icono, int radio, int ancho, int alto) {
        this.imagen = icono.getImage();
        this.radio = radio;

        setPreferredSize(new Dimension(ancho, alto));
        setMinimumSize(new Dimension(ancho, alto));
        setMaximumSize(new Dimension(ancho, alto));

        setOpaque(true); // IMPORTANTE para el fondo
        setBackground(new Color(40, 40, 40)); // fondo por defecto (debug)
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // Fondo
        if (isOpaque()) {
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, w, h, radio * 2, radio * 2);
        }

        // Clip redondeado
        Shape clip = new RoundRectangle2D.Float(
                0, 0, w, h,
                radio * 2f,
                radio * 2f
        );
        g2.setClip(clip);

        // Imagen
        g2.drawImage(imagen, 0, 0, w, h, this);

        g2.dispose();
    }
}
