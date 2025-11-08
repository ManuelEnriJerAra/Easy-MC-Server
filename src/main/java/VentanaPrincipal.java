import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;

public class VentanaPrincipal {
    private static File servidor = null;
    static void setServidor(File servidor) {
        VentanaPrincipal.servidor = servidor;
    }
    static File getServidor() {
        return servidor;
    }

    static File leerServidor() {
        File servidor;
        String servidorString;
        File savedServers = new File("SavedServers.txt");
        try{
            FileReader fr = new FileReader(savedServers);
            BufferedReader br = new BufferedReader(fr);
            servidorString = br.readLine();
            servidor = new File(servidorString);
            if(servidor.exists()){
                br.close();
                fr.close();
                System.out.println("Servidor leido: "+servidorString);
                return servidor;
            }
        } catch (IOException ex ) {}

        return null;
    }

    static void guardarServidor(File servidor) {
        try{
            FileWriter fr = new FileWriter("SavedServers.txt");
            BufferedWriter bw = new BufferedWriter(fr);
            bw.write(servidor.getAbsolutePath());
            bw.close();
            fr.close();
        } catch (IOException e) {}
        System.out.println("Servidor guardado");
    }

    static final String version = "0.0.1";

    static void main(String[] args) {
        setServidor(leerServidor());
        JFrame ventanaPrincipal = new JFrame("Easy MC Server "+version);
        ventanaPrincipal.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        ventanaPrincipal.setSize(800,600);
        ventanaPrincipal.setLocationRelativeTo(null);

        JPanel servidor = new JPanel();
        JLabel servidorLabel = new JLabel("No hay ningún servidor seleccionado.");
        if (getServidor() != null) {
             servidorLabel.setText("Servidor actual: "+getServidor().getAbsolutePath());
        }
        JButton seleccionarServidor = new JButton("Seleccionar Servidor");
        JButton boton = new JButton("Iniciar Servidor");
        JSlider initRamSlider = new JSlider(1024,16384,2048);
        JSlider maxRamSlider = new JSlider(1024,16384,2048);
        JLabel initRamLabel = new JLabel("Mínimo de RAM: "+initRamSlider.getValue()+"M");
        JLabel maxRamLabel = new JLabel("Máximo de RAM: "+maxRamSlider.getValue()+"M");
        initRamSlider.setMajorTickSpacing(1024);
        initRamSlider.setSnapToTicks(true);
        initRamSlider.setPaintTicks(true);
        maxRamSlider.setMajorTickSpacing(1024);
        maxRamSlider.setSnapToTicks(true);
        maxRamSlider.setPaintTicks(true);
        servidor.add(servidorLabel);
        servidor.add(initRamSlider);
        servidor.add(initRamLabel);
        servidor.add(maxRamSlider);
        servidor.add(maxRamLabel);
        servidor.add(boton);
        servidor.add(seleccionarServidor);
        ventanaPrincipal.add(servidor);

        initRamSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                initRamLabel.setText("Mínimo de RAM: "+initRamSlider.getValue()+"M");
            }
        });
        maxRamSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                maxRamLabel.setText("Máximo de RAM: "+maxRamSlider.getValue()+"M");
            }
        });
        seleccionarServidor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JFileChooser fc = new JFileChooser();
                if(fc.showOpenDialog(ventanaPrincipal)==JFileChooser.APPROVE_OPTION) {
                    File archivoSeleccionado = fc.getSelectedFile();
                    guardarServidor(archivoSeleccionado);
                    setServidor(leerServidor());
                }
            }
        });

        boton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ProcessBuilder pb = new ProcessBuilder("java",
                        "-Xms"+initRamSlider.getValue()+"M",
                        "-Xmx"+maxRamSlider.getValue()+"M",
                        "-jar",
                        getServidor().toString());
                pb.inheritIO();
                pb.directory(getServidor().getParentFile());
                try {
                    Process proceso = pb.start();

                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(ventanaPrincipal, "Error al abrir archivo: "+ex.getMessage());
                }
            }
        });

        ventanaPrincipal.setVisible(true);

    }
}
