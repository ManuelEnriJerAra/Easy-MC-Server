package Vista;

import javax.swing.*;
import java.awt.*;

/**
 * FlowLayout que calcula el preferredSize en función del ancho del contenedor (wrap real en JScrollPane).
 */
public class WrapLayout extends FlowLayout {
    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension d = layoutSize(target, false);
        d.width = Math.max(0, d.width - (getHgap() + 1));
        return d;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getWidth();
            if (targetWidth <= 0) {
                targetWidth = Integer.MAX_VALUE;
            }

            Insets insets = target.getInsets();
            int maxWidth = targetWidth - (insets.left + insets.right + getHgap() * 2);
            int x = 0;
            int rowHeight = 0;
            int width = 0;
            int height = insets.top + insets.bottom + getVgap() * 2;

            for (Component m : target.getComponents()) {
                if (!m.isVisible()) continue;
                Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                if (d == null) continue;

                if (x == 0) {
                    x = d.width;
                    rowHeight = d.height;
                } else if (x + getHgap() + d.width <= maxWidth) {
                    x += getHgap() + d.width;
                    rowHeight = Math.max(rowHeight, d.height);
                } else {
                    width = Math.max(width, x);
                    height += rowHeight + getVgap();
                    x = d.width;
                    rowHeight = d.height;
                }
            }

            width = Math.max(width, x);
            height += rowHeight;

            return new Dimension(width + insets.left + insets.right + getHgap() * 2, height);
        }
    }
}

