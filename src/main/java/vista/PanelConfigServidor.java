package vista;

import controlador.GestorServidores;
import modelo.Server;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class PanelConfigServidor extends JPanel {
    private final GestorServidores gestorServidores;
    private final JPanel formPanel;
    private final Map<String, JComponent> editors = new LinkedHashMap<>();
    private Path propertiesPath;

    PanelConfigServidor(GestorServidores gestorServidores){
        this.gestorServidores = gestorServidores;
        this.setLayout(new BorderLayout());
        this.setOpaque(false);

        JLabel titulo = new JLabel("CONFIG");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 18f));

        JButton recargar = new JButton("Recargar");
        JButton guardar = new JButton("Guardar");

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        top.add(titulo, BorderLayout.WEST);

        JPanel topBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        topBtns.setOpaque(false);
        topBtns.add(recargar);
        topBtns.add(guardar);
        top.add(topBtns, BorderLayout.EAST);
        this.add(top, BorderLayout.NORTH);

        formPanel = new JPanel();
        formPanel.setOpaque(false);
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JScrollPane scroll = new JScrollPane(formPanel);
        scroll.setBorder(null);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        this.add(scroll, BorderLayout.CENTER);

        recargar.addActionListener(e -> reload());
        guardar.addActionListener(e -> save());

        SwingUtilities.invokeLater(this::reload);
    }

    void reload(){
        Server server = gestorServidores.getServidorSeleccionado();
        if(server == null || server.getServerDir() == null || server.getServerDir().isBlank()){
            mostrarVacio("No hay servidor seleccionado.");
            return;
        }

        propertiesPath = Path.of(server.getServerDir()).resolve("server.properties");
        if(!Files.exists(propertiesPath)){
            mostrarVacio("No existe server.properties en: " + propertiesPath);
            return;
        }

        Properties props = new Properties();
        try(InputStream in = Files.newInputStream(propertiesPath)){
            props.load(in);
        } catch (IOException ex){
            mostrarVacio("Error leyendo server.properties: " + ex.getMessage());
            return;
        }

        editors.clear();
        formPanel.removeAll();

        java.util.List<String> keys = new ArrayList<>();
        for(Object k : props.keySet()){
            if(k != null) keys.add(String.valueOf(k));
        }
        Collections.sort(keys);

        for(String key : keys){
            String value = props.getProperty(key, "");
            formPanel.add(crearFila(key, value));
            formPanel.add(Box.createVerticalStrut(6));
        }

        formPanel.add(Box.createVerticalGlue());
        formPanel.revalidate();
        formPanel.repaint();
    }

    private void save(){
        if(propertiesPath == null){
            JOptionPane.showMessageDialog(this, "No hay server.properties cargado.", "CONFIG", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Properties out = new Properties();
        for(Map.Entry<String, JComponent> e : editors.entrySet()){
            String key = e.getKey();
            JComponent comp = e.getValue();
            String value;
            if(comp instanceof JCheckBox cb){
                value = cb.isSelected() ? "true" : "false";
            } else if(comp instanceof JTextArea ta){
                value = ta.getText();
            } else if(comp instanceof JTextField tf){
                value = tf.getText();
            } else {
                value = "";
            }
            if(value == null) value = "";
            out.setProperty(key, value);
        }

        try(OutputStream os = Files.newOutputStream(propertiesPath)){
            out.store(os, "Edited by Easy-MC-Server");
        } catch (IOException ex){
            JOptionPane.showMessageDialog(this, "Error guardando: " + ex.getMessage(), "CONFIG", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JOptionPane.showMessageDialog(this, "Guardado correctamente.", "CONFIG", JOptionPane.INFORMATION_MESSAGE);
    }

    private JComponent crearFila(String key, String value){
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel k = new JLabel(key);
        k.setFont(k.getFont().deriveFont(Font.BOLD));
        k.setPreferredSize(new Dimension(220, k.getPreferredSize().height));
        row.add(k, BorderLayout.WEST);

        JComponent editor;
        if("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)){
            JCheckBox cb = new JCheckBox();
            cb.setOpaque(false);
            cb.setSelected("true".equalsIgnoreCase(value));
            editor = cb;
        } else if("motd".equalsIgnoreCase(key) || value.length() > 32){
            JTextArea ta = new JTextArea(value == null ? "" : value, 2, 20);
            ta.setLineWrap(true);
            ta.setWrapStyleWord(true);
            JScrollPane sp = new JScrollPane(ta);
            sp.setBorder(BorderFactory.createLineBorder(new Color(0, 0, 0, 30), 1, true));
            sp.setPreferredSize(new Dimension(200, 56));
            editor = sp;
            // guardamos el textarea como editor lógico, pero mostramos el scroll
            editors.put(key, ta);
            row.add(editor, BorderLayout.CENTER);
            return row;
        } else {
            JTextField tf = new JTextField(value == null ? "" : value);
            editor = tf;
        }

        editors.put(key, editor);
        row.add(editor, BorderLayout.CENTER);
        return row;
    }

    private void mostrarVacio(String msg){
        editors.clear();
        formPanel.removeAll();
        JLabel label = new JLabel(msg);
        label.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10));
        formPanel.add(label);
        formPanel.revalidate();
        formPanel.repaint();
    }
}
