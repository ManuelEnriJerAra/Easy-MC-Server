package Vista;

import javax.swing.*;
import java.awt.*;

public class RoundedBackgroundPanel extends JPanel {
    private int arc;

    RoundedBackgroundPanel(Color background, int arc){
        super(new BorderLayout());
        this.arc = Math.max(0, arc);
        this.setBackground(background);
        this.setOpaque(false);
    }

    void setArc(int arc){
        this.arc = Math.max(0, arc);
        repaint();
    }

    @Override
    protected void paintChildren(Graphics g){
        Graphics2D g2 = (Graphics2D) g.create();
        try{
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Shape old = g2.getClip();
            int w = getWidth();
            int h = getHeight();
            if(arc > 0){
                g2.clip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, arc, arc));
            }
            super.paintChildren(g2);
            g2.setClip(old);
        } finally{
            g2.dispose();
        }
    }

    @Override
    protected void paintComponent(Graphics g){
        Graphics2D g2 = (Graphics2D) g.create();
        try{
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            int w = getWidth();
            int h = getHeight();
            if(arc > 0){
                g2.fillRoundRect(0, 0, w, h, arc, arc);
            } else {
                g2.fillRect(0, 0, w, h);
            }
        } finally{
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
