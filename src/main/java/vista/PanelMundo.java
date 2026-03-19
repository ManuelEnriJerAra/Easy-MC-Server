package vista;

import controlador.GestorServidores;

import javax.swing.*;
import java.awt.*;

public class PanelMundo extends JPanel {
    PanelMundo(GestorServidores gestorServidores){
        this.setLayout(new BorderLayout());
        this.setOpaque(false);

        TitledCardPanel section = new TitledCardPanel("Mundos");
        section.setBorder(BorderFactory.createEmptyBorder());
        this.add(section, BorderLayout.CENTER);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        JButton cambiar = new JButton("Cambiar mundo (pendiente)");
        JButton importar = new JButton("Importar mundo (pendiente)");
        JButton copiar = new JButton("Copiar/duplicar mundo (pendiente)");

        cambiar.setAlignmentX(Component.LEFT_ALIGNMENT);
        importar.setAlignmentX(Component.LEFT_ALIGNMENT);
        copiar.setAlignmentX(Component.LEFT_ALIGNMENT);

        cambiar.addActionListener(e -> JOptionPane.showMessageDialog(this, "Pendiente de implementar.", "Mundo", JOptionPane.INFORMATION_MESSAGE));
        importar.addActionListener(e -> JOptionPane.showMessageDialog(this, "Pendiente de implementar.", "Mundo", JOptionPane.INFORMATION_MESSAGE));
        copiar.addActionListener(e -> JOptionPane.showMessageDialog(this, "Pendiente de implementar.", "Mundo", JOptionPane.INFORMATION_MESSAGE));

        body.add(cambiar);
        body.add(Box.createVerticalStrut(8));
        body.add(importar);
        body.add(Box.createVerticalStrut(8));
        body.add(copiar);
        body.add(Box.createVerticalGlue());

        section.getContentPanel().add(body, BorderLayout.CENTER);
    }
}
