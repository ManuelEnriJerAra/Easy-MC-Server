/*
 * Fichero: VentanaPrincipal.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 * Fecha: 17/01/2026
 *
 * Descripción:
 * Esta es la ventana principal, extiende de JFrame y muestra todo el contenido del servidor, es el encargado de mostrar
 * todos los paneles y es la ventana con la que el usuario más va a interactuar.
 * */

package Vista;

import Controlador.ConsolaOutputStream;
import Controlador.GestorServidores;
import Modelo.Server;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.Consumer;

public class VentanaPrincipal extends JFrame {
    private final JPanel panelIzquierdo, panelDerecho;
    private final PanelConsola panelConsola;
    private Server serverMostrado;
    private Consumer<String> consoleListenerActual;
    private final GestorServidores gestorServidores;

    public VentanaPrincipal(GestorServidores gestorServidores) {
        this.gestorServidores = gestorServidores;
        panelConsola = new PanelConsola(gestorServidores);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setSize(800, 600);
        this.setTitle("Easy-MC-Server");
        this.setLocationRelativeTo(null);
        JPanel ventanaPrincipalPanel = new JPanel(new BorderLayout()); // el panel principal, donde se aloja todo
        ventanaPrincipalPanel.setBackground(Color.LIGHT_GRAY);
        this.setContentPane(ventanaPrincipalPanel);

        // PANEL IZQUIERDO ---------------------------------------------------------------------------------------------
        panelIzquierdo = new JPanel(new BorderLayout()); // este es un panel auxiliar que engloba toda la parte izq
        ventanaPrincipalPanel.add(panelIzquierdo, BorderLayout.WEST);

        // PANEL DE GRÁFICAS DE RENDIMIENTO
        JPanel rendimientoPanel = new JPanel(new GridLayout(1,3));
        panelIzquierdo.add(rendimientoPanel, BorderLayout.NORTH);

        // PANEL DE SERVIDORES
        JPanel servidoresPanel = new JPanel(new BorderLayout());
        panelIzquierdo.add(servidoresPanel, BorderLayout.CENTER);

        // PANEL DE LISTADO DE SERVIDORES
        PanelServidores listaServidoresPanel = getPanelServidores(gestorServidores);
        servidoresPanel.add(listaServidoresPanel, BorderLayout.CENTER);

        // PANEL DE BOTONES DE SERVIDORES
        JPanel botonesServidores = new JPanel(new GridLayout(1,3));
        servidoresPanel.add(botonesServidores, BorderLayout.SOUTH);

        JButton nuevoServerButton = new JButton("+");
        JButton importarServerButton = new JButton("Im");
        JButton borrarServerButton = new JButton("-");

        botonesServidores.add(nuevoServerButton);
        botonesServidores.add(importarServerButton);
        botonesServidores.add(borrarServerButton);

        nuevoServerButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                gestorServidores.crearServidor();
            }
        });
        importarServerButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                gestorServidores.importarServidor();
            }
        });
        borrarServerButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // IMPLEMENTAR BORRADO DE SERVIDOR SELECCIONADO
            }
        });

        new Timer(1000, e -> {
            // actualizar estado cada 1 segundo
            listaServidoresPanel.actualizarEstados();
        }).start();


        // PANEL DERECHO -----------------------------------------------------------------------------------------------

        panelDerecho = new JPanel(new BorderLayout()); // este es un panel auxiliar que engloba toda la parte dcha
        this.add(panelDerecho, BorderLayout.EAST);

        /*
        ventanaPrincipalPanel.add(panelDerecho, BorderLayout.EAST);

        // PANEL DE VISTA PREVIA DEL SERVIDOR
        JPanel vistaPreviaPanel = new JPanel(new BorderLayout());
        panelDerecho.add(vistaPreviaPanel, BorderLayout.NORTH);

        JLabel imagenServidor = new JLabel(gestorServidores.getServidorSeleccionado().getImage());
        vistaPreviaPanel.add(imagenServidor, BorderLayout.WEST);

        // PANEL DE JUGADORES ACTIVOS
        JPanel jugadoresPanel = new JPanel(new BorderLayout());
        panelDerecho.add(jugadoresPanel, BorderLayout.WEST);

        // PANEL DE CONFIGURACIONES GENERALES DEL SERVIDOR
        JPanel configuracionesPanel = new JPanel(new GridBagLayout());
        panelDerecho.add(configuracionesPanel, BorderLayout.CENTER);
        GridBagConstraints c = new GridBagConstraints();
        */
    }
    private void mostrarServidorConsola(Server server, GestorServidores gestorServidores){
        panelDerecho.removeAll();

        panelDerecho.add(new PanelControlServidor(server, gestorServidores), BorderLayout.NORTH);
        panelDerecho.add(panelConsola, BorderLayout.CENTER);

        // Pintar historial actual
        panelConsola.actualizarConsola();

        // Desenganchar listener del server anterior
        if(serverMostrado != null && consoleListenerActual != null){
            serverMostrado.removeConsoleListener(consoleListenerActual);
        }

        // Reenganchar listener
        consoleListenerActual = panelConsola::escribirLinea;
        server.addConsoleListener(consoleListenerActual);
        serverMostrado = server;

        panelDerecho.revalidate();
        panelDerecho.repaint();
    }

    private PanelServidores getPanelServidores(GestorServidores gestorServidores) {
        PanelServidores listaServidoresPanel = new PanelServidores(gestorServidores);
        listaServidoresPanel.setServidorSeleccionadoListener(server -> {
            // compruebo si el servidor ya está seleccionado para ahorrar llamadas
            if(gestorServidores.getServidorSeleccionado() != null && server.getId().equals(gestorServidores.getServidorSeleccionado().getId())) {
                server.appendConsoleLinea("[INFO] El servidor ya está seleccionado.");
            }
            else{
                // establecemos el servidor como seleccionado
                gestorServidores.setServidorSeleccionado(server);
                System.out.println("Servidor seleccionado: " + server.getServerDir());
                mostrarServidorConsola(server, gestorServidores);
            }
        });
        return listaServidoresPanel;
    }
}
