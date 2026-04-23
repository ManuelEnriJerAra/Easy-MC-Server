package controlador.console;

import controlador.console.content.FilesystemServerInstallationDetector;
import controlador.console.content.ServerInstallationReport;
import modelo.Server;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Construye snapshots de contexto para el motor de sugerencias a partir del servidor seleccionado.
 */
public final class ConsoleCommandContextFactory {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> KNOWN_SERVER_TYPES = Set.of(
            "VANILLA", "PAPER", "SPIGOT", "BUKKIT", "FABRIC", "FORGE"
    );
    private final FilesystemServerInstallationDetector installationDetector = new FilesystemServerInstallationDetector();

    public ConsoleCommandContext build(Server server, Set<String> onlinePlayers) {
        ServerInstallationReport report = detectInstallationReport(server);
        ConsoleDynamicDataSnapshot dynamicData = loadDynamicDataSnapshot(server);
        return build(server, onlinePlayers, report, dynamicData);
    }

    public ServerInstallationReport detectInstallationReport(Server server) {
        if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return installationDetector.detect(server);
        }
        return installationDetector.detect(server);
    }

    public ConsoleDynamicDataSnapshot loadDynamicDataSnapshot(Server server) {
        if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return ConsoleDynamicDataSnapshot.empty();
        }
        Path serverDir = Path.of(server.getServerDir());

        Set<String> knownPlayers = new LinkedHashSet<>(loadKnownPlayers(serverDir));
        Set<String> ops = loadJsonNameList(serverDir.resolve("ops.json"), "name");
        Set<String> whitelist = loadJsonNameList(serverDir.resolve("whitelist.json"), "name");
        Set<String> bannedPlayers = loadJsonNameList(serverDir.resolve("banned-players.json"), "name");
        Set<String> bannedIps = loadJsonNameList(serverDir.resolve("banned-ips.json"), "ip");
        knownPlayers.addAll(ops);
        knownPlayers.addAll(whitelist);
        knownPlayers.addAll(bannedPlayers);

        return new ConsoleDynamicDataSnapshot(
                orderedSet(knownPlayers),
                orderedSet(ops),
                orderedSet(whitelist),
                orderedSet(bannedPlayers),
                orderedSet(bannedIps)
        );
    }

    public ConsoleCommandContext build(
            Server server,
            Set<String> onlinePlayers,
            ServerInstallationReport report,
            ConsoleDynamicDataSnapshot dynamicData
    ) {
        if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return ConsoleCommandContext.empty();
        }

        ServerInstallationReport safeReport = report == null ? detectInstallationReport(server) : report;
        ConsoleDynamicDataSnapshot safeDynamicData = dynamicData == null ? loadDynamicDataSnapshot(server) : dynamicData;

        Set<String> online = orderedSet(onlinePlayers);
        LinkedHashSet<String> knownPlayers = new LinkedHashSet<>(safeDynamicData.allKnownPlayersIncludingLists());
        knownPlayers.addAll(online);

        LinkedHashSet<String> dimensions = new LinkedHashSet<>(orderedSet(safeReport.potentialContentSources().stream()
                .filter(source -> source.supportedCategories().contains(SuggestionCategory.DIMENSION))
                .map(source -> source.sourceId().toLowerCase(Locale.ROOT))
                .toList()));
        if (dimensions.isEmpty()) {
            dimensions.add("minecraft:overworld");
            dimensions.add("minecraft:the_nether");
            dimensions.add("minecraft:the_end");
        }

        Set<String> availableTags = orderedSet(safeReport.potentialContentSources().stream()
                .filter(source -> source.supportedCategories().contains(SuggestionCategory.TAG))
                .map(source -> "#" + source.sourceId())
                .toList());

        Map<String, String> metadata = new LinkedHashMap<>(safeReport.metadata());
        metadata.put("helperEndpoint", "");
        metadata.put("brigadierExportAvailable", "false");
        metadata.put("serverOnline", String.valueOf(server.getServerProcess() != null && server.getServerProcess().isAlive()));

        return new ConsoleCommandContext(
                server.getId(),
                server.getDisplayName(),
                server.getServerDir(),
                firstNonBlank(server.getVersion(), safeReport.detectedMinecraftVersion()),
                resolveServerType(server.getTipo(), safeReport.detectedServerType()),
                server.getServerProcess() != null && server.getServerProcess().isAlive(),
                online,
                orderedSet(knownPlayers),
                orderedSet(dimensions),
                availableTags,
                orderedSet(safeReport.mods().stream().map(component -> component.displayName().isBlank() ? component.componentId() : component.displayName()).toList()),
                orderedSet(safeReport.plugins().stream().map(component -> component.displayName().isBlank() ? component.componentId() : component.displayName()).toList()),
                safeReport.contentFingerprint(),
                java.time.Instant.now(),
                safeDynamicData.toDynamicValuesMap(),
                metadata
        );
    }

    private Set<String> loadKnownPlayers(Path serverDir) {
        if (serverDir == null) {
            return Set.of();
        }
        Path usercache = serverDir.resolve("usercache.json");
        if (!Files.isRegularFile(usercache)) {
            return Set.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(usercache.toFile());
            if (root == null || !root.isArray()) {
                return Set.of();
            }
            LinkedHashSet<String> names = new LinkedHashSet<>();
            for (JsonNode entry : root) {
                if (entry == null) {
                    continue;
                }
                String name = entry.path("name").asString("");
                if (!name.isBlank()) {
                    names.add(name.trim());
                }
            }
            return Set.copyOf(names);
        } catch (Exception ex) {
            return Set.of();
        }
    }

    private Set<String> loadJsonNameList(Path file, String fieldName) {
        if (file == null || !Files.isRegularFile(file)) {
            return Set.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(file.toFile());
            if (root == null || !root.isArray()) {
                return Set.of();
            }
            LinkedHashSet<String> values = new LinkedHashSet<>();
            for (JsonNode entry : root) {
                if (entry == null) {
                    continue;
                }
                String value = entry.path(fieldName).asString("");
                if (!value.isBlank()) {
                    values.add(value.trim());
                }
            }
            return Set.copyOf(values);
        } catch (Exception ex) {
            return Set.of();
        }
    }

    private Set<String> orderedSet(java.util.Collection<String> values) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value == null || value.isBlank()) {
                    continue;
                }
                ordered.add(value.trim());
            }
        }
        return Set.copyOf(ordered);
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        return fallback == null ? "" : fallback.trim();
    }

    private String resolveServerType(String preferred, String fallback) {
        String normalizedPreferred = preferred == null ? "" : preferred.trim().toUpperCase(Locale.ROOT);
        if (KNOWN_SERVER_TYPES.contains(normalizedPreferred)) {
            return normalizedPreferred;
        }
        String normalizedFallback = fallback == null ? "" : fallback.trim().toUpperCase(Locale.ROOT);
        if (KNOWN_SERVER_TYPES.contains(normalizedFallback)) {
            return normalizedFallback;
        }
        return normalizedPreferred.isBlank() ? normalizedFallback : normalizedPreferred;
    }
}
