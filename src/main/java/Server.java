public class Server {
    private ServerConfig serverConfig;
    private ServerProperties serverProperties;
    private Process serverProcess;

    public Server(){
        serverProperties = new ServerProperties();
    };

    public Server(ServerConfig serverConfig, ServerProperties serverProperties, Process serverProcess) {
        this.serverConfig = serverConfig;
        this.serverProperties = serverProperties;
        this.serverProcess = serverProcess;
    }

    public ServerConfig getServerConfig() {
        return serverConfig;
    }
    public void setServerConfig(ServerConfig serverConfig) {
        this.serverConfig = serverConfig;
    }
    public ServerProperties getServerProperties() {
        return serverProperties;
    }
    public void setServerProperties(ServerProperties serverProperties) {
        this.serverProperties = serverProperties;
    }
    public Process getServerProcess() {
        return serverProcess;
    }
    public void setServerProcess(Process serverProcess) {
        this.serverProcess = serverProcess;
    }

}
