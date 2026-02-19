/*
 * Fichero: NoServerDialog.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 * Fecha: 17/01/2026
 *
 * Descripción:
 * Esta clase extiende de Dialog y muestra al usuario la ventana de bienvenida cuando no tiene ningún servidor guardado.
 * Muestra información del creador y permite importar y crear un servidor.
 * */

package Vista;

import Controlador.GestorServidores;
import Modelo.Server;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URL;
import java.util.List;

public class NoServerFrame extends JFrame {

    public static ImageIcon logo;

    public NoServerFrame(GestorServidores gestorServidores) {
        // Creo un gestor de servidores que recibirá como argumento la propia ventana, esto es para que
        // cualquier ventana emergente que muestre gestorServidores tenga de padre esta ventana

        this.setTitle("Easy MC Server");
        this.setSize(800, 600);
        this.setLocationRelativeTo(null);
        this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                intentarCerrarAplicacion(gestorServidores);
            }
        });

        // Gestión del logo
        URL urlImagen = getClass().getResource("/logo.png");
        if (urlImagen == null) {
            throw new RuntimeException("No se ha encontrado el logo");
        }
        logo = new ImageIcon(urlImagen);
        Image imagen = logo.getImage().getScaledInstance(150, 150, Image.SCALE_SMOOTH);
        logo = new ImageIcon(imagen);

        // Panel principal del Dialog
        JPanel panelPrincipal = new JPanel(new BorderLayout());
        this.setContentPane(panelPrincipal);

        // PANEL IZQUIERDO ---------------------------------------------------------------------------------------------
        JPanel panelIzquierdo = new JPanel(new BorderLayout());
        panelPrincipal.add(panelIzquierdo, BorderLayout.WEST);
        panelIzquierdo.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

        JLabel logoLabel = new JLabel(logo);
        panelIzquierdo.add(logoLabel, BorderLayout.NORTH);

        JPanel panelInfo = new JPanel(new GridBagLayout());
        panelIzquierdo.add(panelInfo, BorderLayout.CENTER);
        GridBagConstraints c = new GridBagConstraints();

        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;


        JLabel nombreAppLabel = new JLabel("Easy MC Server");
        panelInfo.add(nombreAppLabel, c);

        c.gridy = 1;
        JLabel nombreAutorLabel = new JLabel("<html>Manuel Enrique<br>Jerónimo Aragón</html>");
        panelInfo.add(nombreAutorLabel, c);

        // PANEL DERECHO -----------------------------------------------------------------------------------------------

        JPanel panelDerecho = new JPanel(new GridBagLayout());
        panelPrincipal.add(panelDerecho, BorderLayout.CENTER);
        GridBagConstraints c2 = new GridBagConstraints();

        c2.anchor = GridBagConstraints.CENTER;
        c2.fill = GridBagConstraints.HORIZONTAL;
        c2.gridx = 0;
        c2.gridy = 0;
        c2.gridwidth = 2;
        c2.insets = new Insets(10, 10, 20, 10);

        panelDerecho.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

        JLabel avisoLabel = new JLabel("<html><div style=text-align:center>No hay ningún servidor configurado,<br> importa o crea uno para empezar.</html>");
        avisoLabel.setFont(new Font("Tahoma", Font.BOLD, 16));
        avisoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panelDerecho.add(avisoLabel, c2);


        c2.gridy = 1;

        JPanel botonesNuevo = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        panelDerecho.add(botonesNuevo, c2);
        JButton crearNuevo = new JButton("Crear Nuevo");
        botonesNuevo.add(crearNuevo);
        crearNuevo.addActionListener(e -> {
            Server server = gestorServidores.crearServidor();
            if (server!=null){
                VentanaPrincipal ventanaPrincipal = new VentanaPrincipal(gestorServidores, server);
                ventanaPrincipal.setVisible(true);
                gestorServidores.mostrarAvisoArranqueSiProcede(ventanaPrincipal);
                this.dispose();
            }
        });
        JButton importar = new JButton("Importar");
        importar.addActionListener(e -> {
            Server server = gestorServidores.importarServidor();
            if (server!=null){
                VentanaPrincipal ventanaPrincipal = new VentanaPrincipal(gestorServidores, server);
                ventanaPrincipal.setVisible(true);
                gestorServidores.mostrarAvisoArranqueSiProcede(ventanaPrincipal);
                this.dispose();
            }
        });
        botonesNuevo.add(importar);

    }

    private void intentarCerrarAplicacion(GestorServidores gestorServidores){
        List<Server> activos = gestorServidores.getServidoresActivos();
        if(activos.isEmpty()){
            this.dispose();
            System.exit(0);
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Hay servidores activos.\n\n");
        for(Server s : activos){
            String nombre = (s.getDisplayName() == null || s.getDisplayName().isBlank()) ? s.getId() : s.getDisplayName();
            sb.append("- ").append(nombre).append("\n");
        }
        sb.append("\n¿Quieres cerrarlos antes de salir?");

        Object[] opciones = {"Cerrar servidores y salir", "Cancelar"};
        int res = JOptionPane.showOptionDialog(
                this,
                sb.toString(),
                "Servidores activos",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                opciones,
                opciones[0]
        );
        if(res != 0) return;

        this.setEnabled(false);
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        new Thread(() -> {
            try{
                gestorServidores.detenerServidoresActivosParaSalir();
            } finally{
                SwingUtilities.invokeLater(() -> {
                    try {
                        NoServerFrame.this.dispose();
                    } finally {
                        System.exit(0);
                    }
                });
            }
        }, "shutdown-servidores").start();
    }
}
