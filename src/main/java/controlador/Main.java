/*
 * Fichero: Main.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 * Fecha: 17/01/2026
 *
 * Descripción:
 * Es la clase principal que muestra las ventanas. Contiene la configuración global del programa.
 * */

package controlador;

import modelo.EasyMCConfig;
import vista.NoServerFrame;
import vista.VentanaPrincipal;

import javax.swing.*;
import java.awt.*;

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

    public static void main(String[] args){
        aplicarTemaInicial();
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

    private static void aplicarTemaInicial() {
        EasyMCConfig config = GestorConfiguracion.cargarConfiguracion();
        String temaClassName = config.getTemaClassName();
        if (temaClassName == null || temaClassName.isBlank()) {
            temaClassName = GestorConfiguracion.getTemaPorDefecto();
        }

        try {
            UIManager.setLookAndFeel(temaClassName);
        } catch (Exception e) {
            System.err.println("No se pudo aplicar el tema guardado, se usara el tema por defecto: " + e.getMessage());
            UIManager.put("ClassLoader", Main.class.getClassLoader());
            try {
                UIManager.setLookAndFeel(GestorConfiguracion.getTemaPorDefecto());
                GestorConfiguracion.guardarTema(GestorConfiguracion.getTemaPorDefecto());
            } catch (Exception ex) {
                throw new RuntimeException("No se pudo aplicar el tema por defecto.", ex);
            }
        }
    }
}
