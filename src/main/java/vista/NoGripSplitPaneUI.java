package vista;

import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.*;
import java.awt.*;

public class NoGripSplitPaneUI extends BasicSplitPaneUI {
    @Override
    public BasicSplitPaneDivider createDefaultDivider() {
        BasicSplitPaneDivider divider = new BasicSplitPaneDivider(this) {
            @Override
            public void paint(Graphics g) {
                // Divisor invisible (sin grip/lineas) pero limpia el fondo para evitar artefactos.
                Color bg = getBackground();
                if(bg == null) bg = AppTheme.getBackground();
                if(bg == null) bg = AppTheme.getTransparentColor();
                g.setColor(bg);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        divider.setBorder(null);
        Color bg = AppTheme.getBackground();
        if(bg == null) bg = AppTheme.getTransparentColor();
        divider.setBackground(bg);
        return divider;
    }
}
