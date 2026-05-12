package vista;

import java.awt.*;

/**
 * FlowLayout que calcula el preferredSize en función del ancho del contenedor (wrap real en JScrollPane).
 */
public class WrapLayout extends FlowLayout {
    private final boolean equalCellWidth;
    private final int minimumCellWidth;

    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
        this.equalCellWidth = false;
        this.minimumCellWidth = 0;
    }

    public WrapLayout(int align, int hgap, int vgap, int minimumCellWidth) {
        super(align, hgap, vgap);
        this.equalCellWidth = true;
        this.minimumCellWidth = Math.max(1, minimumCellWidth);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public void layoutContainer(Container target) {
        if (!equalCellWidth) {
            super.layoutContainer(target);
            return;
        }

        synchronized (target.getTreeLock()) {
            Insets insets = target.getInsets();
            int maxWidth = getAvailableWidth(target, insets);
            int cellWidth = getEqualCellWidth(target, maxWidth, true);
            int x = insets.left + getHgap();
            int y = insets.top + getVgap();
            int rowHeight = 0;

            for (Component component : target.getComponents()) {
                if (!component.isVisible()) continue;
                Dimension preferredSize = component.getPreferredSize();
                int componentHeight = preferredSize == null ? 0 : preferredSize.height;

                if (rowHeight > 0 && x + cellWidth > maxWidth + insets.left + getHgap()) {
                    x = insets.left + getHgap();
                    y += rowHeight + getVgap();
                    rowHeight = 0;
                }

                component.setBounds(x, y, cellWidth, componentHeight);
                x += cellWidth + getHgap();
                rowHeight = Math.max(rowHeight, componentHeight);
            }
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension d = layoutSize(target, false);
        d.width = Math.max(0, d.width - (getHgap() + 1));
        return d;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            Insets insets = target.getInsets();
            int maxWidth = getAvailableWidth(target, insets);
            int x = 0;
            int rowHeight = 0;
            int width = 0;
            int height = insets.top + insets.bottom + getVgap() * 2;
            int equalWidth = equalCellWidth ? getEqualCellWidth(target, maxWidth, preferred) : 0;

            for (Component m : target.getComponents()) {
                if (!m.isVisible()) continue;
                Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                if (d == null) continue;
                if (equalCellWidth) {
                    d = new Dimension(equalWidth, d.height);
                }

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

    private int getAvailableWidth(Container target, Insets insets) {
        int targetWidth = target.getWidth();
        if (targetWidth <= 0 && target.getParent() != null) {
            targetWidth = target.getParent().getWidth();
        }
        if (targetWidth <= 0) {
            targetWidth = Integer.MAX_VALUE;
        }
        return Math.max(1, targetWidth - (insets.left + insets.right + getHgap() * 2));
    }

    private int getEqualCellWidth(Container target, int maxWidth, boolean preferred) {
        int naturalWidth = minimumCellWidth;
        for (Component component : target.getComponents()) {
            if (!component.isVisible()) continue;
            Dimension size = preferred ? component.getPreferredSize() : component.getMinimumSize();
            if (size != null) {
                naturalWidth = Math.max(naturalWidth, size.width);
            }
        }

        if (maxWidth == Integer.MAX_VALUE || maxWidth <= naturalWidth) {
            return naturalWidth;
        }

        int columns = Math.max(1, (maxWidth + getHgap()) / (naturalWidth + getHgap()));
        return Math.max(naturalWidth, (maxWidth - (columns - 1) * getHgap()) / columns);
    }
}

