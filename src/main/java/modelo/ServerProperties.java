/*
 * Fichero: ServerProperties.java
 *
 * Autor: Manuel Enrique Jerónimo Aragón
 * Fecha: 17/01/2026
 *
 * Descripción:
 * Esta clase engloba todas las propiedades del archivo server.properties, tiene todas las propiedades del archivo como
 * atributos.
 * */

package modelo;

import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

@Getter
@Setter
@AllArgsConstructor

public class ServerProperties{
    private Server server;

    private Boolean accepts_transfers;
    private Boolean allow_flight; // permitir vuelo
    private Boolean broadcast_console_to_ops;
    private Boolean broadcast_rcon_to_ops;
    private String bug_report_link;
    private String difficulty; // dificultad
    private Boolean enable_code_of_conduct;
    private Boolean enable_jmx_monitoring;
    private Boolean enable_query;
    private Boolean enable_rcon;
    private Boolean enable_status;
    private Boolean enforce_secure_profile;
    private Boolean enforce_whitelist;
    private int entity_broadcast_range_percentage;
    private Boolean force_gamemode; // forzar modo de juego
    private int function_permission_level;
    private String gamemode; // modo de juego
    private Boolean generate_structures; // generar estructuras
    private String generator_settings;
    private Boolean hardcore; // hardcore
    private Boolean hide_online_players;
    private String initial_disabled_packs;
    private String initial_enabled_packs;
    private String level_name;
    private String level_seed;
    private String level_type;
    private Boolean log_ips;
    private Boolean management_server_enabled;
    private String management_server_host;
    private int management_server_port;
    private String management_server_secret;
    private Boolean management_server_tls_enabled;
    private String management_server_tls_keystore;
    private String management_server_tls_keystore_password;
    private int max_chained_neighbor_updates;
    private int max_players;
    private int max_tick_time;
    private int max_world_size;
    private String motd;
    private int network_compression_threshold;
    private Boolean online_mode;
    private int op_permission_level;
    private int pause_when_empty_seconds;
    private int player_idle_timeout;
    private Boolean prevent_proxy_connections;
    private int query_port;
    private int rate_limit;
    private String rcon_password;
    private int rcon_port;
    private String region_file_compression;
    private Boolean require_resource_pack;
    private String resource_pack;
    private String resource_pack_id;
    private String resource_pack_prompt;
    private String resource_pack_sha1;
    private String server_ip;
    private int server_port;
    private int simulation_distance;
    private int spawn_protection;
    private int status_heartbeat_interval;
    private Boolean sync_chunk_writes;
    private String text_filtering_config;
    private String text_filtering_version;
    private Boolean use_native_transport;
    private int view_distance;
    private Boolean white_list;

    public ServerProperties(Server server) {
        this.server = server;
    }

    public ServerProperties() {
        accepts_transfers = false;
        allow_flight = false;
        broadcast_console_to_ops = true;
        broadcast_rcon_to_ops = true;
        bug_report_link = "";
        difficulty=MinecraftConstants.DIFFICULTY_EASY;
        enable_code_of_conduct=false;
        enable_jmx_monitoring=false;
        enable_query=false;
        enable_rcon=false;
        enable_status=true;
        enforce_secure_profile=true;
        enforce_whitelist=false;
        entity_broadcast_range_percentage=100;
        force_gamemode=false;
        function_permission_level=2;
        gamemode = MinecraftConstants.DEFAULT_GAMEMODE;
        generate_structures=true;
        generator_settings="";
        hardcore = false;
        hide_online_players = false;
        initial_disabled_packs = "";
        initial_enabled_packs = "";
        level_name=MinecraftConstants.DEFAULT_WORLD_NAME;
        level_seed="";
        level_type=MinecraftConstants.DEFAULT_WORLD_TYPE_NAMESPACED;
        log_ips=true;
        management_server_enabled=false;
        management_server_host="localhost";
        management_server_port=0;
        management_server_secret="";
        management_server_tls_enabled = true;
        management_server_tls_keystore = "";
        management_server_tls_keystore_password = "";
        max_chained_neighbor_updates = 1000000;
        max_players = 20;
        max_tick_time = 60000;
        max_world_size = 29999984;
        motd = "A Minecraft Server";
        network_compression_threshold = 256;
        online_mode = true;
        op_permission_level = 4;
        pause_when_empty_seconds = 60;
        player_idle_timeout = 0;
        prevent_proxy_connections = false;
        query_port = 25565;
        rate_limit = 0;
        rcon_password = "";
        rcon_port = 25575;
        region_file_compression="deflate";
        require_resource_pack=false;
        resource_pack="";
        resource_pack_id="";
        resource_pack_prompt="";
        resource_pack_sha1="";
        server_ip="";
        server_port=25565;
        simulation_distance=10;
        spawn_protection=16;
        status_heartbeat_interval=0;
        sync_chunk_writes=true;
        text_filtering_config="";
        text_filtering_version="0";
        use_native_transport=true;
        view_distance=10;
        white_list=false;
    }

    public void escribePropiedades(File serverDirectory) throws IOException, IllegalAccessException {
        File propiedades = new File(serverDirectory,"server.properties"); // creamos el archivo server.properties
        try(PrintWriter pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(propiedades), StandardCharsets.UTF_8)))){
            pw.println("# Minecraft server Properties");
            for(Field campo : this.getClass().getDeclaredFields()) {
                // copiamos cada campo, menos si es el servidor
                if(!campo.getName().equals("server")){
                    campo.setAccessible(true);
                    pw.println(propertyKeyForField(campo) + " = " + campo.get(this).toString());
                }
            }
        }
        System.out.println("Escritura de propiedades finalizada");
    }

    // leo las propiedades del server.properties y las introduzco en el objeto
    void leerPropiedades() {
        File serverFolder = new File(server.getServerDir()); // carpeta del servidor
        File properties = new File(serverFolder, "server.properties");
        if (properties.exists()) {
            try{
                BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(properties), StandardCharsets.UTF_8));
                int caracter; // vamos a leer caracter a caracter
                String propiedad; // aqui almaceno la propiedad
                String valor; // aqui almaceno el valor
                StringBuilder builder = new StringBuilder(); // aqui construyo las palabras
                while ((caracter = br.read())!=-1) {
                    // si es un comentario muestro la línea entera y pasamos
                    if(caracter=='#') {
                        br.readLine();
                        //System.out.println("Comentario: "+aux);
                    }
                    // si es un = el caracter significa que hasta ahora hemos estado leyendo una propiedad
                    else if(caracter=='=') {
                        propiedad = builder.toString().trim(); // construyo la propiedad
                        builder.delete(0, builder.length()); // limpio el builder
                        valor = br.readLine().trim(); // el valor es to.do lo que está a la derecha del igual
                        if(!propiedad.isEmpty()) {
                            // recorro cada atributo del objeto para compararlo con cada propiedad
                            for(Field campo : this.getClass().getDeclaredFields()){
                                if(propertyKeyForField(campo).trim().contentEquals(propiedad)){
                                    campo.setAccessible(true);
                                    // si coincide le asigno el valor, mirando primero que tipo es
                                    if(campo.getType().equals(Integer.class) || campo.getType().equals(int.class)) {
                                        campo.set(this, Integer.parseInt(valor));
                                        //System.out.println("La propiedad "+campo.getName() + " se asignó al valor "+valor + " (int)");
                                    }
                                    else if(campo.getType().equals(Boolean.class)) {
                                        campo.set(this, Boolean.parseBoolean(valor));
                                        //System.out.println("La propiedad "+campo.getName() + " se asignó al valor "+valor + " (Boolean)");
                                    }
                                    else{
                                        campo.set(this, valor);
                                        //System.out.println("La propiedad "+campo.getName() + " se asignó al valor "+valor + " (String)");
                                    }
                                }
                            }
                        }
                    }
                    else{
                        // añado cada letra
                        builder.append((char)caracter);
                    }
                }
                br.close();
            } catch (IOException e) {
                System.out.println("Error al leer el archivo propiedades.");
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static String propertyKeyForField(Field campo) {
        if (campo == null) {
            return "";
        }
        return switch (campo.getName()) {
            case "query_port" -> "query.port";
            case "rcon_port" -> "rcon.port";
            default -> campo.getName().replace("_", "-");
        };
    }
}
