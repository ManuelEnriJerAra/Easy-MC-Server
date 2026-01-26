/*
 * Fichero: PanelControlServidor.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 * Fecha: 17/01/2026
 *
 * Descripción:
 * ----------------------
 * */

package Vista;

import Controlador.GestorServidores;
import Modelo.Server;
import com.formdev.flatlaf.extras.components.FlatButton;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

public class PanelControlServidor extends JPanel {
    PanelControlServidor(Server server, GestorServidores gestorServidores) {

        this.setLayout(new BorderLayout());
        JPanel panelBotones = new JPanel(new GridLayout(1,4));
        JButton btnIniciarServidor = new JButton("Iniciar Servidor");
        JButton btnPararServidor = new JButton("Parar Servidor");
        JButton btnReiniciarServidor = new JButton("Reiniciar Servidor");
        FlatButton btnForzarCierreServidor = new FlatButton();
        btnForzarCierreServidor.setText("Forzar Cierre Servidor");
        btnForzarCierreServidor.setOutline(true);
        btnForzarCierreServidor.setBackground(Color.RED);

        btnIniciarServidor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    gestorServidores.iniciarServidor(server);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        btnPararServidor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                gestorServidores.safePararServidor(server);
            }
        });
        btnReiniciarServidor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                gestorServidores.safePararServidor(server);
                try {
                    gestorServidores.iniciarServidor(server);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        btnForzarCierreServidor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                gestorServidores.forzarPararServidor(server);
            }
        });

        panelBotones.add(btnIniciarServidor);
        panelBotones.add(btnPararServidor);
        panelBotones.add(btnReiniciarServidor);
        panelBotones.add(btnForzarCierreServidor);

        this.add(panelBotones,BorderLayout.CENTER);

        this.setVisible(true);
    }



}
