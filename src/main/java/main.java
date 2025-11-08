import tools.jackson.databind.ObjectMapper;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;

public class main {
    static Server server = new Server();

    static void setServidor(Server serverIn) {
        server = serverIn;
    }

    static ServerConfig getServerConfig() {
        return server.getServerConfig();
    }

    static boolean comprobarEstadoServidor() {
        return server.getServerProcess().isAlive();
    }

    public static void copiarArchivo(File origen, File destino) throws IOException {
        try (InputStream in = new FileInputStream(origen);
             OutputStream out = new FileOutputStream(destino)) {

            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
        System.out.println("Archivo copiado a: " + destino.getAbsolutePath());
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

    static void leerPropiedades(ServerConfig serverConfig) {
        File serverConfigFolder = new File(serverConfig.getRuta()).getParentFile();
        File propierties = new File(serverConfigFolder, "server.properties");
        if (propierties.exists()) {
            try{
                BufferedReader br = new BufferedReader(new FileReader(propierties));
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (IOException e) {
                System.out.println("Error al leer el archivo propiedades.");
            }
        }
    }


    public static void main(String[] args) {
        server.setServerConfig(leerServerConfig());
        File carpetaServidor=new File(getServerConfig().getRuta()).getParentFile();
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
        JButton pararServidor = new JButton("Parar Servidor");
        JButton reiniciarServidor = new JButton("Reiniciar Servidor");
        pararServidor.setBackground(new Color(150, 10, 10));
        pararServidor.setForeground(Color.WHITE);
        JSlider initRamSlider = new JSlider(1024,16384,getServerConfig().getRamMin());
        JSlider maxRamSlider = new JSlider(1024,16384,getServerConfig().getRamMax());
        JLabel initRamLabel = new JLabel("Mínimo de RAM: "+initRamSlider.getValue()+"M");
        JLabel maxRamLabel = new JLabel("Máximo de RAM: "+maxRamSlider.getValue()+"M");
        JTextField serverName = new JTextField(server.getServerProperties().getMotd());
        JButton actualizarNombreServidor = new JButton("Actualizar nombre");
        JButton establecerImagen = new JButton("Establecer imagen");
        initRamSlider.setMajorTickSpacing(1024);
        initRamSlider.setSnapToTicks(true);
        initRamSlider.setPaintTicks(true);
        maxRamSlider.setMajorTickSpacing(1024);
        maxRamSlider.setSnapToTicks(true);
        maxRamSlider.setPaintTicks(true);
        JLabel serverStatusLabel = new JLabel("Inactivo");
        serverStatusLabel.setBackground(Color.RED);
        serverStatusLabel.setOpaque(true);
        servidor.add(serverStatusLabel);
        servidor.add(serverName);
        servidor.add(servidorLabel);
        servidor.add(initRamSlider);
        servidor.add(initRamLabel);
        servidor.add(maxRamSlider);
        servidor.add(maxRamLabel);
        servidor.add(iniciarServidor);
        servidor.add(pararServidor);
        servidor.add(reiniciarServidor);
        servidor.add(seleccionarServidor);
        servidor.add(actualizarNombreServidor);
        servidor.add(establecerImagen);
        ventanaPrincipal.add(servidor);

        ProcessBuilder pb = new ProcessBuilder("java",
                "-Xms"+initRamSlider.getValue()+"M",
                "-Xmx"+maxRamSlider.getValue()+"M",
                "-jar",
                getServerConfig().getRuta(),
                "nogui");
        pb.directory(carpetaServidor);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

        //scheduler.scheduleAtFixedRate(comprobarEstadoServidor())

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
                    server.setServerConfig(leerServerConfig());
                    ServerConfig.guardarConfig(serverConfig);
                }
            }
        });

        iniciarServidor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    server.setServerProcess(pb.start());
                    if(comprobarEstadoServidor()) {
                        serverStatusLabel.setText("Servidor iniciado");
                        serverStatusLabel.setBackground(Color.GREEN);
                        System.out.println("Servidor iniciado");
                    }
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(ventanaPrincipal, "Error al abrir archivo: "+ex.getMessage());
                }
            }
        });
        pararServidor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (comprobarEstadoServidor()) {
                    serverStatusLabel.setText("Inactivo");
                    serverStatusLabel.setBackground(Color.RED);
                    OutputStream os = server.getServerProcess().getOutputStream();
                    PrintWriter pw = new PrintWriter(os, true);
                    pw.println("stop");
                    System.out.println("Servidor cerrado");

                }
            }
        });
        ventanaPrincipal.addWindowListener(new  WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (server.getServerProcess()!=null) {
                    if(comprobarEstadoServidor()) {
                        OutputStream os = server.getServerProcess().getOutputStream();
                        PrintWriter pw = new PrintWriter(os, true);
                        pw.println("stop");
                        System.out.println("Servidor cerrado");
                    }
                }
            }
        });
        actualizarNombreServidor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                server.getServerProperties().setMotd(serverName.getText());
                try {
                    server.getServerProperties().escribePropiedades(carpetaServidor);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                } catch (IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        reiniciarServidor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (comprobarEstadoServidor()) {
                    serverStatusLabel.setText("Inactivo");
                    serverStatusLabel.setBackground(Color.RED);
                    OutputStream os = server.getServerProcess().getOutputStream();
                    PrintWriter pw = new PrintWriter(os, true);
                    pw.println("stop");
                    System.out.println("Servidor cerrado");
                }
                try {
                    server.setServerProcess(pb.start());
                    if(comprobarEstadoServidor()) {
                        serverStatusLabel.setText("Servidor iniciado");
                        serverStatusLabel.setBackground(Color.GREEN);
                        System.out.println("Servidor iniciado");
                    }
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(ventanaPrincipal, "Error al abrir archivo: "+ex.getMessage());
                }
            }
        });
        establecerImagen.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JFileChooser fc = new JFileChooser();
                File destino = new File (carpetaServidor, "server-icon.png");
                if(fc.showOpenDialog(ventanaPrincipal)==JFileChooser.APPROVE_OPTION) {
                    File archivoSeleccionado = fc.getSelectedFile();
                    try {
                        copiarArchivo(archivoSeleccionado, destino);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }

                }
            }
        });

        ventanaPrincipal.setVisible(true);

    }
}
