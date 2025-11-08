import tools.jackson.databind.ObjectMapper;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;

public class VentanaPrincipal {
    private static ServerConfig servidor = null;

    static void setServidor(ServerConfig servidorIn) {
        servidor = servidorIn;
    }

    static ServerConfig getServerConfig() {
        return servidor;
    }

    static ServerConfig leerServerConfig() {
        File serverConfigFile = new File("ServerConfig.json");
        ServerConfig serverConfig = null;
        ObjectMapper mapper = new ObjectMapper();
        if (serverConfigFile.exists()) {
            try {
                serverConfig = mapper.readValue(serverConfigFile, ServerConfig.class);
            } catch (Exception e) {
                System.out.println("El archivo ServerConfig.json no contiene ningún servidor válido.");
                return null;
            }
        }
        return serverConfig;
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


    static void main(String[] args) {
        setServidor(leerServerConfig());
        JFrame ventanaPrincipal = new JFrame("Easy MC Server");
        ventanaPrincipal.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        ventanaPrincipal.setSize(800,600);
        ventanaPrincipal.setLocationRelativeTo(null);

        JPanel servidor = new JPanel();
        JLabel servidorLabel = new JLabel("No hay ningún servidor seleccionado.");
        if (getServerConfig() != null) {
             servidorLabel.setText("Servidor actual: "+getServerConfig().getRuta());
        }
        JButton seleccionarServidor = new JButton("Seleccionar Servidor");
        JButton iniciarServidor = new JButton("Iniciar Servidor");
        JSlider initRamSlider = new JSlider(1024,16384,getServerConfig().getRamMin());
        JSlider maxRamSlider = new JSlider(1024,16384,getServerConfig().getRamMax());
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
        servidor.add(iniciarServidor);
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
                    ServerConfig serverConfig = new ServerConfig(initRamSlider.getValue(),  maxRamSlider.getValue(), fc.getSelectedFile().getAbsolutePath());
                    File archivoSeleccionado = fc.getSelectedFile();
                    guardarServidor(archivoSeleccionado);
                    setServidor(leerServerConfig());
                    ServerConfig.guardarConfig(serverConfig);
                }
            }
        });

        iniciarServidor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                File servidor = new File(getServerConfig().getRuta());
                ProcessBuilder pb = new ProcessBuilder("java",
                        "-Xms"+initRamSlider.getValue()+"M",
                        "-Xmx"+maxRamSlider.getValue()+"M",
                        "-jar",
                        getServerConfig().getRuta());
                pb.inheritIO();
                pb.directory(servidor.getParentFile());
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
