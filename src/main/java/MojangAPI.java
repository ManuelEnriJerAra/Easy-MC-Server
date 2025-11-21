import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MojangAPI {
    private static final String MANIFEST_URL="https://launchermeta.mojang.com/mc/game/version_manifest.json";

    private final ObjectMapper mapper = new ObjectMapper();

    // obtener el manifest

    public JsonNode getManifest() {
        try{
            return mapper.readTree(new URL(MANIFEST_URL).openStream());
        }
        catch(Exception e){
            throw new  RuntimeException(e);
        }
    }

    public List<String> obtenerListaVersiones(){
        try{
            JsonNode manifest = getManifest();
            List<String> listaVersiones = new ArrayList<>();

            for(JsonNode node : manifest.get("versions")){
                if(node.get("type").asText().equals("release"))
                    listaVersiones.add(node.get("id").asText());
            }

            return listaVersiones;
        } catch(Exception e){
            throw new  RuntimeException(e);
        }
    }

    public String obtenerUrlVersionJson(String versionId){
        try{
            JsonNode manifest = getManifest();
            for(JsonNode node : manifest.get("versions")){
                if(node.get("id").asText().equals(versionId))
                    return node.get("url").asText();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public String obtenerUrlServerJar(String versionId){
        try{
            String versionJsonUrl = obtenerUrlVersionJson(versionId);
            if (versionJsonUrl == null) return null;

            JsonNode versionJson = mapper.readTree(new URL(versionJsonUrl).openStream());

            JsonNode serverNode = versionJson.get("downloads").get("server");

            return serverNode.get("url").asText();

        } catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    public void descargar(String url, File destino){
        try{
            InputStream in =  new URL(url).openStream();
            FileOutputStream out = new FileOutputStream(destino);
            in.transferTo(out);
            System.out.println("Exito");
            out.close();
            in.close();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    static void main(String[] args) {
        MojangAPI api = new MojangAPI();


        File carpetaPruebas = new File("C:/Users/Manuel Enrique/Documents/ServidorPrueba");
        File serverFile = new File(carpetaPruebas,"server.jar");

        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800,600);
        frame.setLocationRelativeTo(null);

        JComboBox combo = new JComboBox(api.obtenerListaVersiones().toArray());
        frame.add(combo, BorderLayout.WEST);

        JButton descargar = new JButton("Descargar");
        frame.add(descargar, BorderLayout.EAST);

        descargar.addActionListener(e -> {
           api.descargar(api.obtenerUrlServerJar(combo.getSelectedItem().toString()),serverFile);
        });

        frame.setVisible(true);
    }
}
