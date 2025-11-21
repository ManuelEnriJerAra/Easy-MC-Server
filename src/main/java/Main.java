import tools.jackson.databind.ObjectMapper;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.lang.reflect.Field;

public class Main {

    // copiar un archivo sin importar el formato de un origen a un destino
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

    // genera un archivo llamado eula.txt con el texto eula=true en la dirección indicada
    public static void rellenaEULA(File direccion){
        File eula = new File(direccion,"eula.txt");
        try{
            FileWriter fw = new FileWriter(eula);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write("eula=true");
            bw.close();
            fw.close();
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    // devuelve el último servidor que haya en el JSON
    public static Server leerUltimoServidor(){
        File json = new File("ServerConfigList.json");
        ObjectMapper mapper = new ObjectMapper();
        ServerConfig serverConfig = mapper.readValue(json, ServerConfig.class);
        Server server = new Server();
        server.setServerConfig(serverConfig);
        System.out.println("Servidor: " + server.getServerConfig().getRuta());
        if(server.getServerConfig().getRuta()!=null){
            File serverFile = new File(server.getServerConfig().getRuta());
            if(serverFile.exists()){
                return server;
            }
        }
        return null;
    }

    public static Server selectedServer;

    public static void main(String[] args) throws IllegalAccessException {
        // inicialización de la api
        MojangAPI api = new MojangAPI();

        // inicialización del servidor
        selectedServer = leerUltimoServidor();
        if(selectedServer!=null){

        }
        else{
            selectedServer = new Server();
        }
        File carpetaServidor; // creo un file para manejar la carpeta del servidor más fácilmente
        File serverConfigList = new File("ServerConfigList.json"); // creo un file para tener acceso a la lista de servidores guardados
        ProcessBuilder pb; // preparo el processBuilder que va a controlar el proceso del servidor
        if(serverConfigList.length()>0){ // si el JSON tiene texto:
            System.out.println("El JSON no está vacío, se va a inspeccionar.");
        }
        if(selectedServer.getServerConfig().getRuta()==null){ // si el servidor no tiene ruta (no ha sido validado)
            carpetaServidor = null;
        }
        else{ // si el servidor tiene ruta (ha sido inicializado)
            carpetaServidor = new File(selectedServer.getServerConfig().getRuta()).getParentFile();
            selectedServer.getServerProperties().leerPropiedades(selectedServer.getServerConfig());
        }

        String[] modos = { "survival", "creative", "adventure", "spectator" };


        // gestión del Frame ventanaPrincipal
        JFrame ventanaPrincipal = new JFrame("Easy MC Server");
        ventanaPrincipal.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        ventanaPrincipal.setSize(900,600);
        ventanaPrincipal.setLocationRelativeTo(null);

        // gestión del panel servidor, panel principal de ventanaPrincipal
        JPanel servidor = new JPanel(new BorderLayout());

        // servidorLabel: muestra, si hay un servidor seleccionado, su dirección
        JLabel servidorLabel;
        if (selectedServer.getServerConfig().getRuta()!=null){
            servidorLabel = new JLabel("Servidor: " + selectedServer.getServerConfig().getRuta());
        }
        else{
            servidorLabel = new JLabel("No hay ningún servidor seleccionado.");
        }
        servidorLabel.setHorizontalAlignment(JLabel.LEFT);
        servidorLabel.setVerticalAlignment(JLabel.TOP);

        // sliders y sus label correspondientes
        JSlider initRamSlider = new JSlider(1024,16384,selectedServer.getServerConfig().getRamMin());
        JSlider maxRamSlider = new JSlider(1024,16384,selectedServer.getServerConfig().getRamMax());
        JLabel initRamLabel = new JLabel("Mínimo de RAM: "+initRamSlider.getValue()+"M");
        JLabel maxRamLabel = new JLabel("Máximo de RAM: "+maxRamSlider.getValue()+"M");
        initRamSlider.setMajorTickSpacing(1024);
        initRamSlider.setSnapToTicks(true);
        initRamSlider.setPaintTicks(true);
        maxRamSlider.setMajorTickSpacing(1024);
        maxRamSlider.setSnapToTicks(true);
        maxRamSlider.setPaintTicks(true);

        // estado del servidor
        JLabel serverStatusLabel = new JLabel("Inactivo");
        serverStatusLabel.setBackground(Color.RED);
        serverStatusLabel.setOpaque(true);
        serverStatusLabel.setPreferredSize(new Dimension(64,64));
        serverStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        serverStatusLabel.setVerticalAlignment(SwingConstants.CENTER);
        serverStatusLabel.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));

        // panel de propiedades
        JPanel propiedadesPanel = new JPanel();
        propiedadesPanel.setLayout(new BoxLayout(propiedadesPanel, BoxLayout.Y_AXIS));
        propiedadesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        for(Field campo : selectedServer.getServerProperties().getClass().getDeclaredFields()){
            campo.setAccessible(true);
            JPanel fila = new JPanel(new GridBagLayout());
            GridBagConstraints filaC = new GridBagConstraints();
            filaC.anchor = GridBagConstraints.WEST;
            filaC.gridx = 0;
            filaC.gridy = 0;
            filaC.fill = GridBagConstraints.BOTH;
            filaC.weightx = 1.0;
            fila.setMaximumSize(new Dimension(400,64));
            JLabel texto = new JLabel(campo.getName().trim());
            fila.add(texto, filaC);
            JTextField campoText = new JTextField();
            campoText.setPreferredSize(new Dimension(100,20));
            filaC.gridx = 1;
            filaC.gridy = 0;
            filaC.fill = GridBagConstraints.BOTH;
            filaC.weightx = 0.0;
            if(campo.getType()==Boolean.class){
                JCheckBox checkBox = new JCheckBox();
                if((boolean) campo.get(selectedServer.getServerProperties())){
                    checkBox.setSelected(true);
                }
                fila.add(checkBox, filaC);
                checkBox.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        try {
                            campo.set(selectedServer.getServerProperties(), checkBox.isSelected());
                            System.out.println("Campo: "+campo.getName() + " cambia a "+checkBox.isSelected());
                            selectedServer.getServerProperties().escribePropiedades(carpetaServidor);
                        } catch (IllegalAccessException ex) {
                            throw new RuntimeException(ex);
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                });
            }
            else if(campo.getType()==String.class){
                // System.out.println("Campo name: "+texto.getText()+" comparo con gamemode");
                if(texto.getText() == "gamemode"){
                    JComboBox<String> comboBox = new JComboBox(modos);
                    String valorActual = campo.get(selectedServer.getServerProperties()).toString();
                    if(valorActual.contentEquals(modos[0])){
                        comboBox.setSelectedIndex(0);
                    }
                    else  if(valorActual.contentEquals(modos[1])){
                        comboBox.setSelectedIndex(1);
                    }
                    else  if(valorActual.contentEquals(modos[2])){
                        comboBox.setSelectedIndex(2);
                    }
                    else  if(valorActual.contentEquals(modos[3])){
                        comboBox.setSelectedIndex(3);
                    }
                    comboBox.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            try {
                                campo.set(selectedServer.getServerProperties(), comboBox.getSelectedItem().toString());
                                System.out.println("Campo: "+campo.getName() + " cambia a "+ comboBox.getSelectedItem().toString());
                                selectedServer.getServerProperties().escribePropiedades(carpetaServidor);
                            } catch (IllegalAccessException ex) {
                                throw new RuntimeException(ex);
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                    });
                    fila.add(comboBox, filaC);
                }
                else{
                    campoText.setText(campo.get(selectedServer.getServerProperties()).toString());
                    campoText.getDocument().addDocumentListener(new DocumentListener() {
                        @Override
                        public void insertUpdate(DocumentEvent e) {
                            try {
                                campo.set(selectedServer.getServerProperties(), campoText.getText());
                                System.out.println("Campo: "+campo.getName() + " cambia a "+ campoText.getText());
                                selectedServer.getServerProperties().escribePropiedades(carpetaServidor);
                            } catch (IllegalAccessException ex) {
                                throw new RuntimeException(ex);
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        }

                        @Override
                        public void removeUpdate(DocumentEvent e) {
                            try {
                                campo.set(selectedServer.getServerProperties(), campoText.getText());
                                System.out.println("Campo: "+campo.getName() + " cambia a "+ campoText.getText());
                                selectedServer.getServerProperties().escribePropiedades(carpetaServidor);
                            } catch (IllegalAccessException ex) {
                                throw new RuntimeException(ex);
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        }

                        public void changedUpdate(DocumentEvent e) {
                            try {
                                campo.set(selectedServer.getServerProperties(), campoText.getText());
                                System.out.println("Campo: "+campo.getName() + " cambia a "+ campoText.getText());
                                selectedServer.getServerProperties().escribePropiedades(carpetaServidor);
                            } catch (IllegalAccessException ex) {
                                throw new RuntimeException(ex);
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                    });
                    fila.add(campoText, filaC);
                }
            }
            else{
                JSpinner spinner = new JSpinner(new SpinnerNumberModel(campo.getInt(selectedServer.getServerProperties()),0,99999999,1));
                spinner.setPreferredSize(new Dimension(100,20));
                spinner.addChangeListener(new ChangeListener() {
                    public void stateChanged(ChangeEvent e) {
                        try {
                            campo.set(selectedServer.getServerProperties(),spinner.getValue());
                            System.out.println("Campo: "+campo.getName() + " cambia a "+ spinner.getValue());
                            selectedServer.getServerProperties().escribePropiedades(carpetaServidor);
                        } catch (IllegalAccessException ex) {
                            throw new RuntimeException(ex);
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                });
                fila.add(spinner, filaC);
            }
            campoText.setToolTipText(campoText.getText());
            propiedadesPanel.add(fila);
        }
        JScrollPane scrollPane = new JScrollPane(propiedadesPanel);
        scrollPane.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
        scrollPane.setPreferredSize(new Dimension(400, 300));

        //botones
        JButton seleccionarServidor = new JButton("Seleccionar Servidor");
        JButton iniciarServidor = new JButton("Iniciar Servidor");
        JButton pararServidor = new JButton("Parar Servidor");
        JButton reiniciarServidor = new JButton("Reiniciar Servidor");
        JButton establecerImagen = new JButton();
        JButton refrescar = new JButton("Refrescar");
        JButton nuevoServidor =  new JButton("Nuevo Servidor");
        pararServidor.setBackground(new Color(150, 10, 10));
        pararServidor.setForeground(Color.WHITE);

        // panel de gestor de ram
        JPanel gestorRamPanel = new JPanel(new GridLayout(2, 2));
        gestorRamPanel.add(initRamSlider);
        gestorRamPanel.add(initRamLabel);
        gestorRamPanel.add(maxRamSlider);
        gestorRamPanel.add(maxRamLabel);
        gestorRamPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // panel de previsualización de servidor
        // icono del servidor
        JPanel panelPrevisualizar = new JPanel(new BorderLayout());
        ImageIcon iconoServer = new ImageIcon(carpetaServidor+"/server-icon.png");
        JTextField serverName = new JTextField(selectedServer.getServerProperties().getMotd());
        establecerImagen.setIcon(iconoServer);
        establecerImagen.setSize(64,64);
        establecerImagen.setPreferredSize(new Dimension(64,64));

        panelPrevisualizar.add(establecerImagen, BorderLayout.WEST);

        JPanel nombreYdireccion = new JPanel(new BorderLayout());
        nombreYdireccion.add(serverName, BorderLayout.NORTH);
        nombreYdireccion.add(servidorLabel, BorderLayout.SOUTH);
        panelPrevisualizar.add(nombreYdireccion, BorderLayout.CENTER);

        panelPrevisualizar.add(serverStatusLabel, BorderLayout.EAST);

        JPanel botonesPrevisualizar = new JPanel(new GridLayout(1,6));
        botonesPrevisualizar.add(iniciarServidor);
        botonesPrevisualizar.add(pararServidor);
        botonesPrevisualizar.add(reiniciarServidor);
        botonesPrevisualizar.add(seleccionarServidor);
        botonesPrevisualizar.add(refrescar);
        botonesPrevisualizar.add(nuevoServidor);

        panelPrevisualizar.add(botonesPrevisualizar, BorderLayout.SOUTH);

        // gestión de consola
        JPanel consolaPanel =  new JPanel(new BorderLayout());
        JTextArea textArea = new JTextArea();
        JButton send = new JButton("Enviar");
        send.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                OutputStream os = selectedServer.getServerProcess().getOutputStream();
                PrintWriter pw = new PrintWriter(os, true);
                pw.println(textArea.getText());
            }
        });
        consolaPanel.add(textArea, BorderLayout.CENTER);
        consolaPanel.add(send, BorderLayout.SOUTH);

        // añadimos con borderlayout todos los elementos

        servidor.add(panelPrevisualizar,BorderLayout.NORTH);
        servidor.add(scrollPane,BorderLayout.WEST);
        servidor.add(gestorRamPanel,BorderLayout.EAST);
        servidor.add(consolaPanel,BorderLayout.SOUTH);


        ventanaPrincipal.setContentPane(servidor);
        ventanaPrincipal.setVisible(true);


        // gestión de proceso
        if (carpetaServidor != null) {
            pb = new ProcessBuilder("java",
                "-Xms"+initRamSlider.getValue()+"M",
                "-Xmx"+maxRamSlider.getValue()+"M",
                "-jar",
                selectedServer.getServerConfig().getRuta(),
                "nogui");
            pb.directory(carpetaServidor);
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);}
        else {
            pb = null;
        }


        // listeners y actuadores

        //scheduler.scheduleAtFixedRate(comprobarEstadoServidor())
        serverName.getDocument().addDocumentListener(new DocumentListener(){
            public void insertUpdate(DocumentEvent e) {
                selectedServer.getServerProperties().setMotd(serverName.getText());
                try {
                    selectedServer.getServerProperties().escribePropiedades(carpetaServidor);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                } catch (IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                }
            }
            public void removeUpdate(DocumentEvent e) {
                selectedServer.getServerProperties().setMotd(serverName.getText());
                try {
                    selectedServer.getServerProperties().escribePropiedades(carpetaServidor);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                } catch (IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                }
            }
            public void changedUpdate(DocumentEvent e) {
                selectedServer.getServerProperties().setMotd(serverName.getText());
                try {
                    selectedServer.getServerProperties().escribePropiedades(carpetaServidor);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                } catch (IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        initRamSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                initRamLabel.setText("Mínimo de RAM: "+initRamSlider.getValue()+"M");
                selectedServer.getServerConfig().setRamMin(initRamSlider.getValue());
                selectedServer.getServerConfig().guardarServerConfig();

            }
        });
        maxRamSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                maxRamLabel.setText("Máximo de RAM: "+maxRamSlider.getValue()+"M");
                selectedServer.getServerConfig().setRamMax(maxRamSlider.getValue());
                selectedServer.getServerConfig().guardarServerConfig();
            }
        });
        seleccionarServidor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JFileChooser fc = new JFileChooser();
                if(fc.showOpenDialog(ventanaPrincipal)==JFileChooser.APPROVE_OPTION) {
                    ServerConfig serverConfig = new ServerConfig(initRamSlider.getValue(),  maxRamSlider.getValue(), fc.getSelectedFile().getAbsolutePath());
                    File archivoSeleccionado = fc.getSelectedFile();
                    selectedServer.getServerConfig().setRuta(archivoSeleccionado.getAbsolutePath());
                    selectedServer.getServerConfig().guardarServerConfig();
                    selectedServer.setServerConfig(selectedServer.getServerConfig().leerServerConfig());
                    selectedServer.getServerConfig().guardarServerConfig();
                }
            }
        });
        refrescar.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                panelPrevisualizar.updateUI();
                panelPrevisualizar.revalidate();
                panelPrevisualizar.repaint();
            }
        });

        iniciarServidor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                selectedServer.ejecutarServidor();
            }
        });
        pararServidor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    selectedServer.safePararServidor();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        ventanaPrincipal.addWindowListener(new  WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (selectedServer.getServerProcess()!=null) {
                    if(selectedServer.comprobarEstadoServidor()) {
                        OutputStream os = selectedServer.getServerProcess().getOutputStream();
                        PrintWriter pw = new PrintWriter(os, true);
                        pw.println("stop");
                        System.out.println("Servidor cerrado");
                    }
                }
            }
        });
        reiniciarServidor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (selectedServer.comprobarEstadoServidor()) {
                    serverStatusLabel.setText("Inactivo");
                    serverStatusLabel.setBackground(Color.RED);
                    OutputStream os = selectedServer.getServerProcess().getOutputStream();
                    PrintWriter pw = new PrintWriter(os, true);
                    pw.println("stop");
                    System.out.println("Servidor cerrado");
                }
                try {
                    selectedServer.setServerProcess(pb.start());
                    if(selectedServer.comprobarEstadoServidor()) {
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
                ImageIcon icono = new ImageIcon(destino.toString());
                establecerImagen.setIcon(icono);
            }
        });
        nuevoServidor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setAcceptAllFileFilterUsed(false);
                if(chooser.showOpenDialog(ventanaPrincipal)==JFileChooser.APPROVE_OPTION) {
                    int eula = JOptionPane.showConfirmDialog(ventanaPrincipal, "¿Aceptas el EULA de Mojang (https://aka.ms/MinecraftEULA)?", "EULA",  JOptionPane.YES_NO_OPTION);
                    if(eula==JOptionPane.YES_OPTION) {
                        File carpetaSeleccionada = chooser.getSelectedFile();
                        if(!carpetaSeleccionada.isDirectory()) {
                            carpetaSeleccionada = carpetaSeleccionada.getParentFile();
                        }

                        JComboBox versiones = new  JComboBox(api.obtenerListaVersiones().toArray());
                        int seleccion = JOptionPane.showConfirmDialog(ventanaPrincipal, versiones, "Lista Versiones", JOptionPane.OK_CANCEL_OPTION);
                        if(seleccion==JOptionPane.OK_OPTION) {
                            File newCarpeta = new File (carpetaSeleccionada.getAbsoluteFile(), versiones.getSelectedItem().toString()+"_server");
                            System.out.println("NUEVA CARPETA"+newCarpeta.getAbsolutePath());
                            newCarpeta.mkdir();
                            carpetaSeleccionada = newCarpeta;
                            String version = (String) versiones.getSelectedItem();
                            File serverFile = new File(carpetaSeleccionada,version+"_server.jar");
                            api.descargar(api.obtenerUrlServerJar(version), serverFile);
                            rellenaEULA(carpetaSeleccionada);
                            File icono = new File(carpetaSeleccionada, "server-icon.png");
                            try {
                                copiarArchivo(new File("default_image.png"), icono);
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                            selectedServer.inicializarServidor(serverFile);
                        }
                    }
                }
            }
        });
    }
}
