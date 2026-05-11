package controlador.platform;

import modelo.Server;
import modelo.extensions.ServerCapability;
import modelo.extensions.ServerPlatform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class QuiltServerPlatformAdapter extends AbstractServerPlatformAdapter {
    private final QuiltMetaClient metaClient;
    private final ExternalServerInstallerRunner installerRunner;

    public QuiltServerPlatformAdapter() {
        this(new QuiltMetaClient(), new JavaProcessServerInstallerRunner());
    }

    QuiltServerPlatformAdapter(QuiltMetaClient metaClient, ExternalServerInstallerRunner installerRunner) {
        this.metaClient = metaClient == null ? new QuiltMetaClient() : metaClient;
        this.installerRunner = installerRunner == null ? new JavaProcessServerInstallerRunner() : installerRunner;
    }

    @Override
    public ServerPlatform getPlatform() {
        return ServerPlatform.QUILT;
    }

    @Override
    public int getDetectionPriority() {
        return 86;
    }

    @Override
    public ServerPlatformProfile detect(Path serverDir) {
        Path executableJar = resolveJarSilently(serverDir);
        boolean hasQuiltFiles = exists(serverDir, "quilt-server-launch.jar")
                || exists(serverDir, "quilt_installer.json")
                || existsQuiltNamedFile(serverDir);
        boolean hasQuiltJarMarkers = MinecraftServerJarInspector.looksLikeQuiltServerJar(executableJar);
        if (!hasQuiltFiles && !hasQuiltJarMarkers) {
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
        return ServerPlatform.QUILT.defaultCapabilities();
    }

    @Override
    public boolean supportsAutomatedCreation() {
        return true;
    }

    @Override
    public String getCreationDisplayName() {
        return "Quilt";
    }

    @Override
    public List<ServerCreationOption> listCreationOptions() throws IOException {
        return metaClient.listCreationOptions();
    }

    @Override
    public void install(Server server, ServerInstallationRequest request) throws IOException {
        ServerPlatformInstallSupport.requireServerAndRequest(server, request, "Quilt");
        Files.createDirectories(request.targetDirectory());
        QuiltMetaClient.QuiltSelection selection = QuiltMetaClient.decodePlatformVersion(request.platformVersion());
        String installerUrl = metaClient.installerUrl(request.platformVersion());
        if (installerUrl == null || installerUrl.isBlank()) {
            throw new IOException("No se ha podido obtener el instalador de Quilt.");
        }

        String installerVersion = selection.installerVersion();
        Path installerJar = request.targetDirectory().resolve(
                installerVersion == null || installerVersion.isBlank()
                        ? "quilt-installer.jar"
                        : "quilt-installer-" + installerVersion + ".jar"
        );
        if (!Files.isRegularFile(installerJar)) {
            request.downloader().download(installerUrl, installerJar.toFile());
        }

        List<String> arguments = new ArrayList<>();
        arguments.add("install");
        arguments.add("server");
        arguments.add(request.minecraftVersion());
        if (selection.loaderVersion() != null && !selection.loaderVersion().isBlank()) {
            arguments.add("--loader-version=" + selection.loaderVersion());
        }
        arguments.add("--download-server");
        arguments.add("--install-dir=" + request.targetDirectory().toAbsolutePath());
        installerRunner.installServer(installerJar, request.targetDirectory(), arguments);

        ServerPlatformInstallSupport.prepareCommonFiles(
                server,
                request,
                ServerPlatform.QUILT,
                selection.loaderVersion(),
                "Easy-MC Quilt " + request.minecraftVersion(),
                List.of(request.targetDirectory().resolve("mods"), request.targetDirectory().resolve("config"))
        );
    }

    @Override
    public ProcessBuilder buildStartProcess(Server server, Path executableJar) {
        Path serverDir = Path.of(server.getServerDir());
        Path launcher = serverDir.resolve("quilt-server-launch.jar");
        if (Files.isRegularFile(launcher)) {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "java",
                    "-Xms" + server.getServerConfig().getRamInit() + "M",
                    "-Xmx" + server.getServerConfig().getRamMax() + "M",
                    "-jar",
                    launcher.toString(),
                    "nogui"
            );
            processBuilder.directory(serverDir.toFile());
            processBuilder.redirectErrorStream(true);
            return processBuilder;
        }
        return super.buildStartProcess(server, executableJar);
    }

    private boolean existsQuiltNamedFile(Path serverDir) {
        if (serverDir == null || !Files.isDirectory(serverDir)) {
            return false;
        }
        try (var stream = Files.list(serverDir)) {
            return stream.anyMatch(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).contains("quilt"));
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
