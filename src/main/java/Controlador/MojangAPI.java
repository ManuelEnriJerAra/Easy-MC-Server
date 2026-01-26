/*
 * Fichero: MojangAPI.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 * Fecha: 17/01/2026
 *
 * Descripción:
 * Esta clase es la encargada de hacer todas las consultas a la API de Mojang.
 * */

package Controlador;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.swing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MojangAPI {
    private static final String VERSION_MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";

    private final ObjectMapper mapper = new ObjectMapper();

    public JsonNode getManifest() {
        try{
            return mapper.readTree(new URL(VERSION_MANIFEST_URL).openStream());
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
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
