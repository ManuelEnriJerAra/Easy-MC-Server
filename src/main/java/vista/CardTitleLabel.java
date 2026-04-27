package vista;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;

import javax.swing.JLabel;

final class CardTitleLabel extends JLabel {
    CardTitleLabel() {
        super();
    }

    CardTitleLabel(String text) {
        super(text);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension preferred = super.getPreferredSize();
        String text = getText();
        if (text == null || text.isBlank() || getFont() == null) {
            return preferred;
        }

        FontMetrics metrics = getFontMetrics(getFont());
        if (metrics == null) {
            return preferred;
        }

        FontRenderContext frc = metrics.getFontRenderContext();
        GlyphVector glyphVector = getFont().createGlyphVector(frc, text);
        Rectangle2D visualBounds = glyphVector.getVisualBounds();
        Insets insets = getInsets();
        int tightHeight = (int) Math.ceil(visualBounds.getHeight()) + insets.top + insets.bottom;
        return new Dimension(preferred.width, Math.max(1, tightHeight));
    }
}
