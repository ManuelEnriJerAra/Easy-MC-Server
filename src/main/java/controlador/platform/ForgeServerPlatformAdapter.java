package controlador.platform;

import controlador.Utilidades;
import modelo.extensions.ServerCapability;
import modelo.extensions.ServerPlatform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class ForgeServerPlatformAdapter extends AbstractServerPlatformAdapter {
    private final ForgeRepositoryClient forgeRepositoryClient;
    private final ForgeInstallerRunner forgeInstallerRunner;

    public ForgeServerPlatformAdapter() {
        this(new ForgeRepositoryClient(), new JavaProcessForgeInstallerRunner());
    }

    ForgeServerPlatformAdapter(ForgeRepositoryClient forgeRepositoryClient, ForgeInstallerRunner forgeInstallerRunner) {
        this.forgeRepositoryClient = forgeRepositoryClient == null ? new ForgeRepositoryClient() : forgeRepositoryClient;
        this.forgeInstallerRunner = forgeInstallerRunner == null ? new JavaProcessForgeInstallerRunner() : forgeInstallerRunner;
    }

    @Override
    public ServerPlatform getPlatform() {
        return ServerPlatform.FORGE;
    }

    @Override
    public int getDetectionPriority() {
        return 80;
    }

    @Override
    public ServerPlatformProfile detect(Path serverDir) {
        Path executableJar = resolveJarSilently(serverDir);
        boolean hasForgeDirectories = (existsDirectory(serverDir, "mods")
                || existsDirectory(serverDir, "config"))
                && (existsDirectory(serverDir, "libraries")
                || exists(serverDir, "unix_args.txt")
                || exists(serverDir, "win_args.txt")
                || exists(serverDir, "run.sh")
                || exists(serverDir, "run.bat")
                || exists(serverDir, "user_jvm_args.txt"));
        boolean hasForgeJarMarkers = MinecraftServerJarInspector.looksLikeForgeServerJar(executableJar);
        if (!hasForgeDirectories && !hasForgeJarMarkers) {
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
        return ServerPlatform.FORGE.defaultCapabilities();
    }

    @Override
    public boolean supportsAutomatedCreation() {
        return true;
    }

    @Override
    public String getCreationDisplayName() {
        return "Forge";
    }

    @Override
    public List<ServerCreationOption> listCreationOptions() throws IOException {
        return forgeRepositoryClient.listCreationOptions();
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
        boolean hasForgeRuntime = exists(serverDir, "run.bat")
                || exists(serverDir, "run.sh")
                || exists(serverDir, "user_jvm_args.txt")
                || existsDirectory(serverDir, "libraries");
        return (hasServerJar || hasForgeRuntime)
                ? ServerValidationResult.ok()
                : ServerValidationResult.error("No se ha encontrado una instalacion valida de Forge.");
    }

    @Override
    public void install(modelo.Server server, ServerInstallationRequest request) throws IOException {
        if (server == null) {
            throw new IOException("El servidor no es valido.");
        }
        if (request == null || request.targetDirectory() == null) {
            throw new IOException("No se ha indicado la carpeta de instalacion.");
        }
        if (request.minecraftVersion() == null || request.minecraftVersion().isBlank()) {
            throw new IOException("No se ha indicado la version de Minecraft.");
        }
        if (request.platformVersion() == null || request.platformVersion().isBlank()) {
            throw new IOException("No se ha indicado la version de Forge.");
        }
        if (request.downloader() == null) {
            throw new IOException("No se ha proporcionado un descargador de archivos.");
        }

        Files.createDirectories(request.targetDirectory());
        String installerUrl = forgeRepositoryClient.getInstallerUrl(request.platformVersion());
        if (installerUrl == null || installerUrl.isBlank()) {
            throw new IOException("No se ha podido construir la URL del instalador de Forge.");
        }

        Path installerJar = request.targetDirectory().resolve("forge-" + request.platformVersion() + "-installer.jar");
        if (!Files.isRegularFile(installerJar)) {
            request.downloader().download(installerUrl, installerJar.toFile());
        }

        forgeInstallerRunner.installServer(installerJar, request.targetDirectory());
        Files.createDirectories(request.targetDirectory().resolve("mods"));
        Files.createDirectories(request.targetDirectory().resolve("config"));

        if (request.acceptEula()) {
            Utilidades.rellenaEULA(request.targetDirectory().toFile());
        }
        if (request.defaultIconSource() != null && Files.isRegularFile(request.defaultIconSource())) {
            Utilidades.copiarArchivo(request.defaultIconSource().toFile(), request.targetDirectory().resolve("server-icon.png").toFile());
        }
        Utilidades.escribirPuertoEnProperties(request.targetDirectory(), 25565);
        Utilidades.escribirMotdEnProperties(request.targetDirectory(), "Easy-MC Forge " + request.minecraftVersion());

        server.setServerDir(request.targetDirectory().toString());
        server.setVersion(request.minecraftVersion());
        server.setLoaderVersion(request.platformVersion());
        server.setPlatform(ServerPlatform.FORGE);
    }

    @Override
    public ProcessBuilder buildStartProcess(modelo.Server server, Path executableJar) {
        Path serverDir = Path.of(server.getServerDir());
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path windowsLauncher = serverDir.resolve("run.bat");
        Path unixLauncher = serverDir.resolve("run.sh");
        if (isWindows && Files.isRegularFile(windowsLauncher)) {
            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", windowsLauncher.toString(), "nogui");
            processBuilder.directory(serverDir.toFile());
            processBuilder.redirectErrorStream(true);
            return processBuilder;
        }
        if (!isWindows && Files.isRegularFile(unixLauncher)) {
            ProcessBuilder processBuilder = new ProcessBuilder("sh", unixLauncher.toString(), "nogui");
            processBuilder.directory(serverDir.toFile());
            processBuilder.redirectErrorStream(true);
            return processBuilder;
        }
        Path argsFile = findForgeArgsFile(serverDir, isWindows);
        if (argsFile != null && Files.isRegularFile(argsFile)) {
            java.util.List<String> command = new java.util.ArrayList<>();
            command.add("java");
            Path userJvmArgs = serverDir.resolve("user_jvm_args.txt");
            if (Files.isRegularFile(userJvmArgs)) {
                command.add("@" + userJvmArgs.toString());
            }
            command.add("@" + argsFile.toString());
            command.add("nogui");

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(serverDir.toFile());
            processBuilder.redirectErrorStream(true);
            return processBuilder;
        }
        if (executableJar == null) {
            throw new IllegalArgumentException("No se ha encontrado run.bat/run.sh, args de Forge ni .jar ejecutable.");
        }
        return super.buildStartProcess(server, executableJar);
    }

    @Override
    public boolean requiresExecutableJarForStart() {
        return false;
    }

    private Path findForgeArgsFile(Path serverDir, boolean windows) {
        if (serverDir == null) {
            return null;
        }
        String targetName = windows ? "win_args.txt" : "unix_args.txt";
        try (var paths = Files.walk(serverDir.resolve("libraries"), 8)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase(targetName))
                    .findFirst()
                    .orElse(null);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private Path resolveJarSilently(Path serverDir) {
        try {
            return resolveExecutableJar(serverDir);
        } catch (RuntimeException ex) {
            return null;
        } catch (Exception ex) {
            return null;
        }
    }
}
