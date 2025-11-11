import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;
import tools.jackson.databind.ObjectMapper;

import java.io.*;
import java.lang.reflect.Field;

@AllArgsConstructor
@Getter
@Setter

public class ServerProperties {
    private Boolean accepts_transfers;
    private Boolean allow_flight;
    private Boolean broadcast_console_to_ops;
    private Boolean broadcast_rcon_to_ops;
    private String bug_report_link;
    private String difficulty;
    private Boolean enable_code_of_conduct;
    private Boolean enable_jmx_monitoring;
    private Boolean enable_query;
    private Boolean enable_rcon;
    private Boolean enable_status;
    private Boolean enforce_secure_profile;
    private Boolean enforce_whitelist;
    private int entity_broadcast_range_percentage;
    private Boolean force_gamemode;
    private int function_permission_level;
    private String gamemode;
    private Boolean generate_structures;
    private String generator_settings;
    private Boolean hardcore;
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

    public ServerProperties() {
        accepts_transfers = false;
        allow_flight = false;
        broadcast_console_to_ops = true;
        broadcast_rcon_to_ops = true;
        bug_report_link = "";
        difficulty="easy";
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
        gamemode = "survival";
        generate_structures=true;
        generator_settings="";
        hardcore = false;
        hide_online_players = false;
        initial_disabled_packs = "";
        initial_enabled_packs = "";
        level_name="world";
        level_seed="";
        level_type="minecrat\\:normal";
        log_ips=true;
        management_server_enabled=false;
        management_server_host="localhost";
        management_server_port=0;
        management_server_secret="hReRtEXF3Vn7lHBc8mRpCp7dFEH0a8EWvwiKgPSe";
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
        File propiedades = new File(serverDirectory,"server.properties");
        FileWriter fw = new FileWriter(propiedades);
        PrintWriter pw = new PrintWriter(fw);
        pw.println("# Minecraft server Properties");
        for(Field campo : this.getClass().getDeclaredFields()) {
            campo.setAccessible(true);
            pw.println(campo.getName().replace("_","-") + " = " + campo.get(this).toString());
        }
        pw.close();
        fw.close();
        System.out.println("Escritura de propiedades finalizada");
    }

    void leerPropiedades(ServerConfig serverConfig) {
        File serverConfigFolder = new File(serverConfig.getRuta()).getParentFile();
        File propierties = new File(serverConfigFolder, "server.properties");
        if (propierties.exists()) {
            try{
                BufferedReader br = new BufferedReader(new FileReader(propierties));
                int caracter;
                String propiedad;
                String valor;
                String aux;
                StringBuilder builder = new StringBuilder();
                while ((caracter = br.read())!=-1) {
                    if(caracter=='#') {
                        aux = br.readLine();
                        System.out.println("Comentario: "+aux);
                    } else if(caracter=='=') {
                        propiedad = builder.toString().trim();
                        builder.delete(0, builder.length());
                        valor = br.readLine().trim();
                        if(propiedad.length()>0) {
                            for(Field campo : this.getClass().getDeclaredFields()){
                                if(campo.getName().replace("_","-").trim().contentEquals(propiedad)){
                                    campo.setAccessible(true);
                                    if(campo.getType().equals(Integer.class) || campo.getType().equals(int.class)) {
                                        campo.set(this, Integer.parseInt(valor));
                                        System.out.println("La propiedad "+campo.getName() + " se asignó al valor "+valor + " (int)");
                                    }
                                    else if(campo.getType().equals(Boolean.class)) {
                                        campo.set(this, Boolean.parseBoolean(valor));
                                        System.out.println("La propiedad "+campo.getName() + " se asignó al valor "+valor + " (Boolean)");
                                    }
                                    else{
                                        campo.set(this, valor);
                                        System.out.println("La propiedad "+campo.getName() + " se asignó al valor "+valor + " (String)");
                                    }
                                }
                            }
                        }
                    }
                    else{
                        builder.append((char)caracter);
                    }
                }
            } catch (IOException e) {
                System.out.println("Error al leer el archivo propiedades.");
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
