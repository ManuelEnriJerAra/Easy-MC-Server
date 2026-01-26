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

import Vista.NoServerDialog;
import Vista.VentanaPrincipal;

import javax.swing.*;
import java.awt.*;

import com.formdev.flatlaf.*;
import com.formdev.flatlaf.intellijthemes.*;
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialLighterIJTheme;
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTSolarizedLightIJTheme;
import com.formdev.flatlaf.util.SystemInfo;

public class Main {

    public static NoServerDialog noServerDialog;
    public static VentanaPrincipal ventanaPrincipal;

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
            UIManager.put("defaultFont", new Font("SegoeUI", Font.PLAIN, 12));
            UIManager.put("Button.arc", 20); // radio de los botones
            UIManager.put("Component.arc", 20); // radio de los componentes (RoundBorder)
        });


        GestorServidores gestorServidores = new GestorServidores();
        if(gestorServidores.getListaServidores().isEmpty()){
            noServerDialog = new NoServerDialog(gestorServidores);
            noServerDialog.setVisible(true);
        }
        else{
            ventanaPrincipal = new VentanaPrincipal(gestorServidores);
            ventanaPrincipal.setVisible(true);
        }
    }
}
