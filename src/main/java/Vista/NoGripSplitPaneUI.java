package Vista;

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
                if(bg == null) bg = UIManager.getColor("Panel.background");
                if(bg == null) bg = new Color(0, 0, 0, 0);
                g.setColor(bg);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        divider.setBorder(null);
        Color bg = UIManager.getColor("Panel.background");
        if(bg == null) bg = new Color(0, 0, 0, 0);
        divider.setBackground(bg);
        return divider;
    }
}
