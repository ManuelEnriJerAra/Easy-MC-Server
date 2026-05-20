package controlador.platform;

import modelo.Server;
import modelo.extensions.ServerCapability;
import modelo.extensions.ServerPlatform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class NeoForgeServerPlatformAdapter extends AbstractServerPlatformAdapter {
    private final NeoForgeRepositoryClient repositoryClient;
    private final ForgeInstallerRunner installerRunner;

    public NeoForgeServerPlatformAdapter() {
        this(new NeoForgeRepositoryClient(), new JavaProcessForgeInstallerRunner());
    }

    NeoForgeServerPlatformAdapter(NeoForgeRepositoryClient repositoryClient, ForgeInstallerRunner installerRunner) {
        this.repositoryClient = repositoryClient == null ? new NeoForgeRepositoryClient() : repositoryClient;
        this.installerRunner = installerRunner == null ? new JavaProcessForgeInstallerRunner() : installerRunner;
    }

    @Override
    public ServerPlatform getPlatform() {
        return ServerPlatform.NEOFORGE;
    }

    @Override
    public int getDetectionPriority() {
        return 90;
    }

    @Override
    public ServerPlatformProfile detect(Path serverDir) {
        Path executableJar = resolveJarSilently(serverDir);
        boolean hasNeoForgeMarkers = existsNeoForgeNamedFile(serverDir)
                || hasNeoForgeRuntimePath(serverDir)
                || MinecraftServerJarInspector.looksLikeNeoForgeServerJar(executableJar);
        if (!hasNeoForgeMarkers) {
            return null;
        }
        ServerValidationResult validation = validate(serverDir);
        return validation.valid() ? buildProfileAllowingMissingJar(serverDir, null) : null;
    }

    @Override
    public List<Path> getExtensionDirectories(Path serverDir) {
        return serverDir == null ? List.of() : List.of(serverDir.resolve("mods"));
    }

    @Override
    public Set<ServerCapability> getCapabilities() {
        return ServerPlatform.NEOFORGE.defaultCapabilities();
    }

    @Override
    public boolean supportsAutomatedCreation() {
        return true;
    }

    @Override
    public boolean supportsUnstableCreationOptions() {
        return true;
    }

    @Override
    public String getCreationDisplayName() {
        return "NeoForge";
    }

    @Override
    public List<ServerCreationOption> listCreationOptions() throws IOException {
        return repositoryClient.listCreationOptions();
    }

    @Override
    public ServerValidationResult validate(Path serverDir) {
        if (serverDir == null) {
            return ServerValidationResult.error("No se ha indicado el directorio del servidor.");
        }
        if (!Files.isDirectory(serverDir)) {
            return ServerValidationResult.error("No existe la carpeta del servidor.");
        }
        Path executableJar = resolveJarSilently(serverDir);
        boolean hasServerJar = executableJar != null && Files.isRegularFile(executableJar)
                && MinecraftServerJarInspector.looksLikeMinecraftServerJar(executableJar);
        boolean hasRuntime = exists(serverDir, "run.bat")
                || exists(serverDir, "run.sh")
                || exists(serverDir, "user_jvm_args.txt")
                || existsDirectory(serverDir, "libraries");
        return (hasServerJar || hasRuntime)
                ? ServerValidationResult.ok()
                : ServerValidationResult.error("No se ha encontrado una instalación válida de NeoForge.");
    }

    @Override
    public void install(Server server, ServerInstallationRequest request) throws IOException {
        ServerPlatformInstallSupport.requireServerAndRequest(server, request, "NeoForge");
        Files.createDirectories(request.targetDirectory());
        String installerUrl = repositoryClient.getInstallerUrl(request.platformVersion());
        if (installerUrl == null || installerUrl.isBlank()) {
            throw new IOException("No se ha podido construir la URL del instalador de NeoForge.");
        }

        Path installerJar = request.targetDirectory().resolve("neoforge-" + request.platformVersion() + "-installer.jar");
        if (!Files.isRegularFile(installerJar)) {
            request.downloader().download(installerUrl, installerJar.toFile());
        }

        installerRunner.installServer(installerJar, request.targetDirectory());
        ServerPlatformInstallSupport.prepareCommonFiles(
                server,
                request,
                ServerPlatform.NEOFORGE,
                request.platformVersion(),
                "Dora NeoForge " + request.minecraftVersion(),
                List.of(request.targetDirectory().resolve("mods"), request.targetDirectory().resolve("config"))
        );
    }

    @Override
    public ProcessBuilder buildStartProcess(Server server, Path executableJar) {
        return new ForgeServerPlatformAdapter().buildStartProcess(server, executableJar);
    }

    @Override
    public boolean requiresExecutableJarForStart() {
        return false;
    }

    private boolean existsNeoForgeNamedFile(Path serverDir) {
        if (serverDir == null || !Files.isDirectory(serverDir)) {
            return false;
        }
        try (var stream = Files.list(serverDir)) {
            return stream.anyMatch(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).contains("neoforge"));
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private boolean hasNeoForgeRuntimePath(Path serverDir) {
        if (serverDir == null || !Files.isDirectory(serverDir.resolve("libraries"))) {
            return false;
        }
        try (var paths = Files.walk(serverDir.resolve("libraries"), 6)) {
            return paths.anyMatch(path -> path.toString()
                    .replace('\\', '/')
                    .toLowerCase(java.util.Locale.ROOT)
                    .contains("/net/neoforged/neoforge/"));
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
