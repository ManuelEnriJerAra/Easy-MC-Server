package controlador.console.vanilla;

import controlador.MojangAPI;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Descubre automaticamente los comandos vanilla root arrancando una instancia temporal del servidor oficial.
 */
public final class AutomaticVanillaCommandCatalogSource implements VanillaCommandCatalogSource {
    private static final Pattern HELP_COMMAND_PATTERN = Pattern.compile("^.*?:\\s+/([a-z0-9_-]+)(?:\\s|$|\\s*->)");
    private static final String CACHE_KIND = "runtime-help-cache";
    private static final ExecutorService WARMUP_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "vanilla-command-warmup");
        thread.setDaemon(true);
        return thread;
    });

    private final ObjectMapper objectMapper;
    private final Path cacheDirectory;
    private final MojangAPI mojangAPI;
    private final LocalJavaRuntimeLocator javaRuntimeLocator;
    private final Map<String, VanillaCommandCatalog> memoryCache = new ConcurrentHashMap<>();
    private final Map<String, Object> locksByVersion = new ConcurrentHashMap<>();
    private final Map<String, java.util.concurrent.CompletableFuture<VanillaCommandCatalog>> warmupsByVersion = new ConcurrentHashMap<>();

    public AutomaticVanillaCommandCatalogSource() {
        this(new ObjectMapper(),
                Path.of(System.getProperty("user.home"), ".easy-mc-server", "cache", "console", "vanilla", "commands"),
                new MojangAPI(),
                new LocalJavaRuntimeLocator());
    }

    AutomaticVanillaCommandCatalogSource(
            ObjectMapper objectMapper,
            Path cacheDirectory,
            MojangAPI mojangAPI,
            LocalJavaRuntimeLocator javaRuntimeLocator
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.cacheDirectory = Objects.requireNonNull(cacheDirectory, "cacheDirectory");
        this.mojangAPI = Objects.requireNonNull(mojangAPI, "mojangAPI");
        this.javaRuntimeLocator = Objects.requireNonNull(javaRuntimeLocator, "javaRuntimeLocator");
    }

    @Override
    public VanillaCommandCatalog resolve(String version) {
        String normalizedVersion = version == null ? "" : version.trim();
        if (normalizedVersion.isBlank()) {
            return new VanillaCommandCatalog("", List.of(), false, CACHE_KIND, Instant.now(), Map.of("error", "missing-version"));
        }

        VanillaCommandCatalog inMemory = memoryCache.get(normalizedVersion);
        if (inMemory != null) {
            return inMemory;
        }

        synchronized (locksByVersion.computeIfAbsent(normalizedVersion, ignored -> new Object())) {
            VanillaCommandCatalog cached = memoryCache.get(normalizedVersion);
            if (cached != null && !cached.version().isBlank()) {
                return cached;
            }

            Optional<VanillaCommandCatalog> fromDisk = readPersistentCache(normalizedVersion);
            if (fromDisk.isPresent()) {
                memoryCache.put(normalizedVersion, fromDisk.get());
                return fromDisk.get();
            }

            VanillaCommandCatalog generated = generate(normalizedVersion);
            memoryCache.put(normalizedVersion, generated);
            if (!generated.commands().isEmpty()) {
                writePersistentCache(generated);
            }
            return generated;
        }
    }

    @Override
    public VanillaCommandCatalog resolveCached(String version) {
        String normalizedVersion = version == null ? "" : version.trim();
        if (normalizedVersion.isBlank()) {
            return failureCatalog("", Instant.now(), "missing-version");
        }

        VanillaCommandCatalog inMemory = memoryCache.get(normalizedVersion);
        if (inMemory != null) {
            return inMemory;
        }

        Optional<VanillaCommandCatalog> fromDisk = readPersistentCache(normalizedVersion);
        if (fromDisk.isPresent()) {
            memoryCache.put(normalizedVersion, fromDisk.get());
            return fromDisk.get();
        }

        if (warmupsByVersion.containsKey(normalizedVersion)) {
            return failureCatalog(normalizedVersion, Instant.now(), "warmup-pending");
        }
        return failureCatalog(normalizedVersion, Instant.now(), "cache-miss");
    }

    @Override
    public void scheduleWarmup(String version) {
        String normalizedVersion = version == null ? "" : version.trim();
        if (normalizedVersion.isBlank()) {
            return;
        }
        if (memoryCache.containsKey(normalizedVersion) || readPersistentCache(normalizedVersion).isPresent()) {
            return;
        }

        warmupsByVersion.computeIfAbsent(normalizedVersion, key ->
                java.util.concurrent.CompletableFuture.supplyAsync(() -> resolve(key), WARMUP_EXECUTOR)
                        .whenComplete((ignored, throwable) -> warmupsByVersion.remove(key))
        );
    }

    @Override
    public boolean isWarmupPending(String version) {
        String normalizedVersion = version == null ? "" : version.trim();
        return !normalizedVersion.isBlank() && warmupsByVersion.containsKey(normalizedVersion);
    }

    private VanillaCommandCatalog generate(String version) {
        Instant now = Instant.now();
        try {
            JsonNode versionJson = mojangAPI.obtenerVersionJson(version);
            int requiredJavaVersion = versionJson.path("javaVersion").path("majorVersion").asInt(17);
            String serverJarUrl = versionJson.path("downloads").path("server").path("url").asText("");
            if (serverJarUrl.isBlank()) {
                return failureCatalog(version, now, "missing-server-jar-url");
            }

            Optional<InstalledJavaRuntime> javaRuntime = javaRuntimeLocator.findCompatibleRuntime(requiredJavaVersion);
            if (javaRuntime.isEmpty()) {
                return failureCatalog(version, now, "missing-compatible-java-" + requiredJavaVersion);
            }

            Path versionCacheDir = cacheDirectory.resolve(version);
            Files.createDirectories(versionCacheDir);
            Path bundledJar = versionCacheDir.resolve("server-bundled.jar");
            if (!Files.isRegularFile(bundledJar)) {
                mojangAPI.descargar(serverJarUrl, bundledJar.toFile(), null);
            }

            LinkedHashSet<String> commands = extractCommands(version, bundledJar, javaRuntime.get(), versionCacheDir.resolve("work"));
            if (commands.isEmpty()) {
                return failureCatalog(version, now, "empty-help-output");
            }

            return new VanillaCommandCatalog(
                    version,
                    List.copyOf(commands),
                    false,
                    CACHE_KIND,
                    now,
                    Map.of(
                            "requiredJavaVersion", String.valueOf(requiredJavaVersion),
                            "javaExecutable", javaRuntime.get().executable().toString(),
                            "discovery", "temporary-server-help"
                    )
            );
        } catch (Exception ex) {
            return failureCatalog(version, now, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private LinkedHashSet<String> extractCommands(
            String version,
            Path bundledJar,
            InstalledJavaRuntime javaRuntime,
            Path workDirectory
    ) throws IOException, InterruptedException {
        recreateDirectory(workDirectory);
        writeMinimalServerFiles(workDirectory);

        List<String> outputLines = new ArrayList<>();
        Process process = startTemporaryServer(javaRuntime.executable(), bundledJar, workDirectory);
        try {
            BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));

            long deadline = System.nanoTime() + 120_000_000_000L;
            boolean helpRequested = false;

            while (System.nanoTime() < deadline && process.isAlive()) {
                helpRequested |= drainOutput(stdout, outputLines, process, true);
                drainOutput(stderr, outputLines, process, false);
                if (helpRequested) {
                    break;
                }
                Thread.sleep(100L);
            }

            long stopDeadline = System.nanoTime() + 15_000_000_000L;
            while (System.nanoTime() < stopDeadline && process.isAlive()) {
                drainOutput(stdout, outputLines, process, false);
                drainOutput(stderr, outputLines, process, false);
                Thread.sleep(50L);
            }
        } finally {
            stopProcess(process);
        }

        Files.write(workDirectory.resolve("command-help.log"), outputLines, StandardCharsets.UTF_8);

        LinkedHashSet<String> commands = new LinkedHashSet<>();
        for (String line : outputLines) {
            Matcher matcher = HELP_COMMAND_PATTERN.matcher(line);
            if (matcher.find()) {
                commands.add(matcher.group(1).toLowerCase(Locale.ROOT));
            }
        }
        return commands;
    }

    private boolean drainOutput(
            BufferedReader reader,
            List<String> outputLines,
            Process process,
            boolean triggerHelpWhenReady
    ) throws IOException {
        boolean helpRequested = false;
        while (reader.ready()) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            outputLines.add(line);
            if (triggerHelpWhenReady && line.contains("Done (")) {
                process.getOutputStream().write(("help" + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
                process.getOutputStream().write(("stop" + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
                process.getOutputStream().flush();
                helpRequested = true;
            }
        }
        return helpRequested;
    }

    private Process startTemporaryServer(Path javaExecutable, Path bundledJar, Path workDirectory) throws IOException {
        List<String> command = List.of(
                javaExecutable.toString(),
                "-Xms256M",
                "-Xmx512M",
                "-Djava.awt.headless=true",
                "-jar",
                bundledJar.toString(),
                "nogui"
        );
        return new ProcessBuilder(command)
                .directory(workDirectory.toFile())
                .redirectErrorStream(false)
                .start();
    }

    private void writeMinimalServerFiles(Path workDirectory) throws IOException {
        Files.writeString(workDirectory.resolve("eula.txt"), "eula=true" + System.lineSeparator(), StandardCharsets.UTF_8);

        int port = reserveFreePort();
        String properties = String.join(System.lineSeparator(),
                "server-port=" + port,
                "online-mode=false",
                "motd=easy-mc-command-cache",
                "level-type=minecraft:flat",
                "generate-structures=false",
                "spawn-monsters=false",
                "enable-rcon=false",
                "enable-command-block=false",
                "max-world-size=64",
                "view-distance=2",
                "simulation-distance=2",
                "network-compression-threshold=-1",
                "sync-chunk-writes=false",
                "");
        Files.writeString(workDirectory.resolve("server.properties"), properties, StandardCharsets.UTF_8);
    }

    private int reserveFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    private void recreateDirectory(Path directory) throws IOException {
        if (Files.isDirectory(directory)) {
            try (var walk = Files.walk(directory)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignored) {
                            }
                        });
            }
        }
        Files.createDirectories(directory);
    }

    private void stopProcess(Process process) {
        if (process == null) {
            return;
        }
        if (process.isAlive()) {
            process.destroy();
            try {
                process.waitFor();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }

    private Optional<VanillaCommandCatalog> readPersistentCache(String version) {
        Path path = cacheDirectory.resolve(version).resolve("commands.json");
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(path)) {
            JsonNode root = objectMapper.readTree(in);
            List<String> commands = new ArrayList<>();
            JsonNode commandsNode = root.path("commands");
            if (commandsNode.isArray()) {
                for (JsonNode node : commandsNode) {
                    String value = node.asText("").trim();
                    if (!value.isBlank()) {
                        commands.add(value.toLowerCase(Locale.ROOT));
                    }
                }
            }
            commands = sanitizeCommands(commands);
            if (commands.isEmpty()) {
                return Optional.empty();
            }
            Map<String, String> metadata = new LinkedHashMap<>();
            JsonNode metadataNode = root.path("metadata");
            if (metadataNode.isObject()) {
                for (var entry : metadataNode.properties()) {
                    metadata.put(entry.getKey(), entry.getValue().asText(""));
                }
            }
            return Optional.of(new VanillaCommandCatalog(
                    root.path("version").asText(version),
                    commands,
                    true,
                    root.path("source").asText(CACHE_KIND),
                    parseInstant(root.path("generatedAt").asText("")),
                    metadata
            ));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private void writePersistentCache(VanillaCommandCatalog catalog) {
        try {
            Path versionDirectory = cacheDirectory.resolve(catalog.version());
            Files.createDirectories(versionDirectory);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("version", catalog.version());
            payload.put("source", catalog.source());
            payload.put("generatedAt", catalog.generatedAt().toString());
            payload.put("commands", sanitizeCommands(catalog.commands()));
            payload.put("metadata", catalog.metadata());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(versionDirectory.resolve("commands.json").toFile(), payload);
        } catch (IOException ignored) {
        }
    }

    private List<String> sanitizeCommands(List<String> rawCommands) {
        if (rawCommands == null || rawCommands.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        for (String command : rawCommands) {
            String normalized = command == null ? "" : command.trim().toLowerCase(Locale.ROOT);
            if (normalized.isBlank() || normalized.contains(":")) {
                continue;
            }
            sanitized.add(normalized);
        }
        return List.copyOf(sanitized);
    }

    private VanillaCommandCatalog failureCatalog(String version, Instant now, String error) {
        return new VanillaCommandCatalog(
                version,
                List.of(),
                false,
                CACHE_KIND,
                now,
                Map.of(
                        "error", error == null ? "unknown" : error,
                        "pendingWarmup", String.valueOf("warmup-pending".equals(error) || "cache-miss".equals(error))
                )
        );
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return Instant.now();
        }
    }
}
