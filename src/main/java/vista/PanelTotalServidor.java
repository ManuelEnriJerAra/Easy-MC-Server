package vista;

import controlador.GestorServidores;

import javax.swing.JPanel;
import java.awt.BorderLayout;

public class PanelTotalServidor extends JPanel {
    PanelTotalServidor(GestorServidores gestorServidores){
        this.setLayout(new BorderLayout());
        this.setOpaque(true);
        this.setBackground(AppTheme.getPanelBackground());

        PanelPrevisualizacion panelPrevisualizacion = new PanelPrevisualizacion(gestorServidores);
        this.add(panelPrevisualizacion, BorderLayout.NORTH);
    }

    @Override
    public void updateUI(){
        super.updateUI();
        this.setBackground(AppTheme.getPanelBackground());
        // this.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
    }
}
