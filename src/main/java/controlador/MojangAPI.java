/*
 * Fichero: MojangAPI.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 * Fecha: 17/01/2026
 *
 * Descripción:
 * Esta clase es la encargada de hacer todas las consultas a la API de Mojang.
 * */

package controlador;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MojangAPI {
    private static final String VERSION_MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    private static final String UUID_BY_USERNAME_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 8_000;
    private static final ExecutorService BACKGROUND_REQUESTS = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "mojang-api-worker");
        thread.setDaemon(true);
        return thread;
    });

    private final ObjectMapper mapper = new ObjectMapper();

    public static void runBackgroundRequest(Runnable task) {
        if (task == null) {
            return;
        }
        BACKGROUND_REQUESTS.execute(task);
    }

    public JsonNode getManifest() {
        try{
            return readJsonFromUrl(VERSION_MANIFEST_URL);
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
                if(node.get("type").asString().equals("release"))
                    listaVersiones.add(node.get("id").asString());
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
                if(node.get("id").asString().equals(versionId))
                    return node.get("url").asString();
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

            JsonNode versionJson = readJsonFromUrl(versionJsonUrl);

            JsonNode serverNode = versionJson.get("downloads").get("server");

            return serverNode.get("url").asString();

        } catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    public JsonNode obtenerVersionJson(String versionId) {
        try {
            String versionJsonUrl = obtenerUrlVersionJson(versionId);
            if (versionJsonUrl == null || versionJsonUrl.isBlank()) {
                throw new IllegalArgumentException("No se encontro metadata para la version " + versionId);
            }
            return readJsonFromUrl(versionJsonUrl);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public interface DownloadProgressListener {
        void onProgress(long bytesLeidos, long totalBytes);
    }

    public void descargar(String url, File destino, DownloadProgressListener listener){
        try{
            URLConnection conn = openConnection(url);
            long total = conn.getContentLengthLong();

            try(InputStream in = conn.getInputStream();
                FileOutputStream out = new FileOutputStream(destino)){

                byte[] buffer = new byte[8192];
                long leidos = 0;
                int n;
                while((n = in.read(buffer)) != -1){
                    out.write(buffer, 0, n);
                    leidos += n;
                    if(listener != null){
                        listener.onProgress(leidos, total);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ImageIcon obtenerCabezaJugador(String username, int sizePx){
        if(username == null || username.isBlank()) return null;
        if(sizePx <= 0) return null;

        try{
            String uuid = obtenerUuidPorUsername(username);
            if(uuid == null) return null;

            String skinUrl = obtenerUrlSkinPorUuid(uuid);
            if(skinUrl == null) return null;

            BufferedImage skin = readImageFromUrl(skinUrl);
            if(skin == null) return null;

            BufferedImage head = extraerCabezaDesdeSkin(skin);

            BufferedImage out = new BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            // La cara del jugador es pixel-art: usar nearest-neighbor para evitar blur
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.drawImage(head, 0, 0, sizePx, sizePx, null);
            g.dispose();

            return new ImageIcon(out);
        } catch (Exception e){
            return null;
        }
    }

    private String obtenerUuidPorUsername(String username){
        try{
            JsonNode node = readJsonFromUrl(UUID_BY_USERNAME_URL + username);
            JsonNode idNode = node.get("id");
            if(idNode == null) return null;
            String id = idNode.asString();
            return (id == null || id.isBlank()) ? null : id;
        } catch (Exception e){
            return null;
        }
    }

    private String obtenerUrlSkinPorUuid(String uuid){
        try{
            JsonNode profile = readJsonFromUrl(SESSION_PROFILE_URL + uuid);
            JsonNode props = profile.get("properties");
            if(props == null || !props.isArray() || props.isEmpty()) return null;

            for(JsonNode prop : props){
                if(prop == null) continue;
                if(!"textures".equals(prop.path("name").asString())) continue;
                String value = prop.path("value").asString();
                if(value == null || value.isBlank()) continue;

                byte[] decoded = Base64.getDecoder().decode(value);
                JsonNode texturesJson = mapper.readTree(decoded);
                JsonNode skinUrl = texturesJson.path("textures").path("SKIN").path("url");
                String url = skinUrl.asString();
                return (url == null || url.isBlank()) ? null : url;
            }
            return null;
        } catch (Exception e){
            return null;
        }
    }

    private BufferedImage extraerCabezaDesdeSkin(BufferedImage skin){
        // Formato skin clásico: cara en (8,8) 8x8; capa (sombrero) en (40,8) 8x8
        BufferedImage base = skin.getSubimage(8, 8, 8, 8);
        BufferedImage overlay = skin.getSubimage(40, 8, 8, 8);

        BufferedImage out = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(base, 0, 0, null);
        g.drawImage(overlay, 0, 0, null);
        g.dispose();
        return out;
    }

    private JsonNode readJsonFromUrl(String url) throws IOException {
        try (InputStream in = openConnection(url).getInputStream()) {
            return mapper.readTree(in);
        }
    }

    private BufferedImage readImageFromUrl(String url) throws IOException {
        try (InputStream in = openConnection(url).getInputStream()) {
            return ImageIO.read(in);
        }
    }

    private URLConnection openConnection(String url) throws IOException {
        URLConnection connection = URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        return connection;
    }
}
