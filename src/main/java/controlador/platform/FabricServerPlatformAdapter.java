package controlador.platform;

import modelo.Server;
import modelo.extensions.ServerCapability;
import modelo.extensions.ServerPlatform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class FabricServerPlatformAdapter extends AbstractServerPlatformAdapter {
    private final FabricMetaClient metaClient;

    public FabricServerPlatformAdapter() {
        this(new FabricMetaClient());
    }

    FabricServerPlatformAdapter(FabricMetaClient metaClient) {
        this.metaClient = metaClient == null ? new FabricMetaClient() : metaClient;
    }

    @Override
    public ServerPlatform getPlatform() {
        return ServerPlatform.FABRIC;
    }

    @Override
    public int getDetectionPriority() {
        return 85;
    }

    @Override
    public ServerPlatformProfile detect(Path serverDir) {
        Path executableJar = resolveJarSilently(serverDir);
        boolean hasFabricFiles = exists(serverDir, "fabric-server-launcher.properties")
                || exists(serverDir, "fabric-server-launch.properties")
                || existsFabricNamedFile(serverDir);
        boolean hasFabricJarMarkers = MinecraftServerJarInspector.looksLikeFabricServerJar(executableJar);
        if (!hasFabricFiles && !hasFabricJarMarkers) {
            return null;
        }
        ServerValidationResult validation = validate(serverDir);
        return validation.valid() ? buildProfile(serverDir, null) : null;
    }

    @Override
    public List<Path> getExtensionDirectories(Path serverDir) {
        return serverDir == null ? List.of() : List.of(serverDir.resolve("mods"));
    }

    @Override
    public Set<ServerCapability> getCapabilities() {
        return ServerPlatform.FABRIC.defaultCapabilities();
    }

    @Override
    public boolean supportsAutomatedCreation() {
        return true;
    }

    @Override
    public String getCreationDisplayName() {
        return "Fabric";
    }

    @Override
    public List<ServerCreationOption> listCreationOptions() throws IOException {
        return metaClient.listCreationOptions();
    }

    @Override
    public void install(Server server, ServerInstallationRequest request) throws IOException {
        ServerPlatformInstallSupport.requireServerAndRequest(server, request, "Fabric");
        Files.createDirectories(request.targetDirectory());
        FabricMetaClient.FabricSelection selection = FabricMetaClient.decodePlatformVersion(request.platformVersion());
        String downloadUrl = metaClient.downloadUrl(request.minecraftVersion(), request.platformVersion());
        Path destinationJar = request.targetDirectory().resolve(
                "fabric-server-mc." + request.minecraftVersion()
                        + "-loader." + selection.loaderVersion()
                        + "-launcher." + selection.installerVersion()
                        + ".jar"
        );
        if (!Files.isRegularFile(destinationJar)) {
            request.downloader().download(downloadUrl, destinationJar.toFile());
        }
        ServerPlatformInstallSupport.prepareCommonFiles(
                server,
                request,
                ServerPlatform.FABRIC,
                selection.loaderVersion(),
                "Easy-MC Fabric " + request.minecraftVersion(),
                List.of(request.targetDirectory().resolve("mods"), request.targetDirectory().resolve("config"))
        );
    }

    private boolean existsFabricNamedFile(Path serverDir) {
        if (serverDir == null || !Files.isDirectory(serverDir)) {
            return false;
        }
        try (var stream = Files.list(serverDir)) {
            return stream.anyMatch(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).contains("fabric"));
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private Path resolveJarSilently(Path serverDir) {
        try {
            return resolveExecutableJar(serverDir);
        } catch (Exception e) {
            return null;
        }
    }
}
