/*
 * Fichero: Utilidades.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 * Fecha: 17/01/2026
 *
 * Descripción:
 * Esta clase contiene diferentes métodos que utilizan otras clases. Son métodos varios como copiar un archivo o extraer
 * un porcentaje de una línea de texto.
 * */

package controlador;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

public class Utilidades {
    public static void copiarArchivo(File origen, File destino){
        try{
            InputStream in = new FileInputStream(origen);
            OutputStream out = new FileOutputStream(destino);

            byte[] buffer = new byte[1024]; // creamos un buffer para leer de 1KB en 1KB
            int length;
            while((length = in.read(buffer))>0){
                out.write(buffer, 0, length);
            }
            in.close();
            out.close();
        } catch (IOException e){
            throw new RuntimeException(e);
        }
        System.out.println("Archivo copiado a: "+ destino.getAbsolutePath());
    }

    public static void copiarDirectorio(Path origen, Path destino) throws IOException {
        if(origen == null || destino == null) throw new IllegalArgumentException("Las rutas no pueden ser nulas.");
        if(!Files.isDirectory(origen)) throw new IllegalArgumentException("El origen no es un directorio: " + origen);

        try(Stream<Path> walk = Files.walk(origen)){
            for(Path source : walk.toList()){
                Path relative = origen.relativize(source);
                Path target = destino.resolve(relative);
                if(Files.isDirectory(source)){
                    Files.createDirectories(target);
                } else {
                    if(target.getParent() != null){
                        Files.createDirectories(target.getParent());
                    }
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    public static void eliminarDirectorio(Path directorio) throws IOException {
        if(directorio == null || !Files.exists(directorio)) return;

        try(Stream<Path> walk = Files.walk(directorio)){
            for(Path path : walk.sorted(Comparator.reverseOrder()).toList()){
                Files.deleteIfExists(path);
            }
        }
    }

    public static void moverDirectorio(Path origen, Path destino) throws IOException {
        if(origen == null || destino == null) throw new IllegalArgumentException("Las rutas no pueden ser nulas.");
        if(!Files.isDirectory(origen)) throw new IllegalArgumentException("El origen no es un directorio: " + origen);

        try{
            Files.move(origen, destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex){
            copiarDirectorio(origen, destino);
            eliminarDirectorio(origen);
        }
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

    public static ImageIcon escalarIcono(ImageIcon icono, int ancho, int alto){
        Image img = icono.getImage();

        BufferedImage bi = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bi.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(img, 0, 0, ancho, alto, null);
        g2d.dispose();

        return new ImageIcon(bi);
    }

    // Esta función encuentra un archivo jar a partir de un servidor. Si lo encuentra devuelve el nombre, si no NULL
    public static Path encontrarEjecutableJar(Path direccion){
        try (Stream<Path> archivos = Files.list(direccion)){
            // filtramos y nos quedamos solo con los archivos jar
            List<Path> jars = archivos.filter(path -> path.toString().toLowerCase().endsWith(".jar")).toList();
            if(jars.isEmpty()){ // vacío
                throw new IllegalStateException("No hay ningún jar en la dirección "+direccion);
            }
            if(jars.size()>1){ // más de un jar
                throw new IllegalStateException("Se ha encontrado más de un jar en la dirección "+direccion);
            }
            return jars.get(0);
        } catch (IOException e){
            System.out.println("No se ha podido encontrar el jar: "+e.getMessage());
            return null;
        }
    }

    public static int extraerPorcentaje(String line) {
        int indicePorcentaje = line.indexOf("%");

        if (indicePorcentaje == -1) return 0; // por seguridad

        // Tomamos la parte izquierda del %
        String izquierda = line.substring(0, indicePorcentaje);

        // Ahora nos quedamos solo con lo que hay después del último espacio
        String numero = izquierda.substring(
                izquierda.lastIndexOf(" ") + 1
        );

        return Integer.parseInt(numero);
    }

    public static String leerMotdDesdeProperties(Path direccion){
        File file = direccion.resolve("server.properties").toFile();
        if(!file.exists()) return null;

        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream(file)) {
            properties.load(fis);
            return properties.getProperty("motd");
        } catch (IOException e) {
            return null;
        }

    }

    public static void escribirPuertoEnProperties(Path direccion, int puerto){
        if(direccion == null) return;
        if(puerto <= 0) return;

        Path propertiesPath = direccion.resolve("server.properties");

        Properties properties = new Properties();
        if(Files.exists(propertiesPath)){
            try (FileInputStream fis = new FileInputStream(propertiesPath.toFile())) {
                properties.load(fis);
            } catch (IOException ignored) {
            }
        }

        properties.setProperty("server-port", String.valueOf(puerto));

        try (FileOutputStream fos = new FileOutputStream(propertiesPath.toFile())) {
            properties.store(fos, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void escribirMotdEnProperties(Path direccion, String motd){
        if(direccion == null) return;
        if(motd == null) motd = "";

        Path propertiesPath = direccion.resolve("server.properties");

        Properties properties = new Properties();
        if(Files.exists(propertiesPath)){
            try (FileInputStream fis = new FileInputStream(propertiesPath.toFile())) {
                properties.load(fis);
            } catch (IOException ignored) {
            }
        }

        properties.setProperty("motd", motd);

        try (FileOutputStream fos = new FileOutputStream(propertiesPath.toFile())) {
            properties.store(fos, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String fromMStoDateString(Long ms){
        System.out.println("Me ha llegado el valor: "+ms.toString());
        return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    }

}

