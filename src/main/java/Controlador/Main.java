/*
 * Fichero: Main.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 * Fecha: 17/01/2026
 *
 * Descripción:
 * Es la clase principal que muestra las ventanas. Contiene la configuración global del programa.
 * */

package Controlador;

import Vista.NoServerFrame;
import Vista.VentanaPrincipal;

import javax.swing.*;
import java.awt.*;

import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTSolarizedLightIJTheme;
import com.formdev.flatlaf.util.SystemInfo;

public class Main {

    public static NoServerFrame noServerFrame;
    public static VentanaPrincipal ventanaPrincipal;

    public static final int DEFAULT_ARC = 20;

    public static void aplicarPreferenciasUI() {
        UIManager.put("defaultFont", new Font("SegoeUI", Font.PLAIN, 12));
        UIManager.put("Button.arc", DEFAULT_ARC); // radio de los botones
        UIManager.put("Component.arc", DEFAULT_ARC); // radio de los componentes (RoundBorder)
        UIManager.put("ProgressBar.arc", DEFAULT_ARC);
    }

    static void main(String[] args){
        FlatMTSolarizedLightIJTheme.setup();
        //FlatLightFlatIJTheme.setup();
        SwingUtilities.invokeLater(()->{
            if (SystemInfo.isLinux) {
                JFrame.setDefaultLookAndFeelDecorated(true);
                JDialog.setDefaultLookAndFeelDecorated(true);
            }
            System.setProperty("flatlaf.uiScale", "1");
            System.setProperty("flatlaf.useWindowDecorations",  "true");
            System.setProperty("flatlaf.MenuBarEmbedded", "true");
            aplicarPreferenciasUI();
        });

        GestorServidores gestorServidores = new GestorServidores();
        if(gestorServidores.getListaServidores().isEmpty()){
            noServerFrame = new NoServerFrame(gestorServidores);
            noServerFrame.setVisible(true);
            gestorServidores.mostrarAvisoArranqueSiProcede(noServerFrame);
        }
        else{
            ventanaPrincipal = new VentanaPrincipal(gestorServidores);
            ventanaPrincipal.setVisible(true);
            gestorServidores.mostrarAvisoArranqueSiProcede(ventanaPrincipal);
        }
    }
}
