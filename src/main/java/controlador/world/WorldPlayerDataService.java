package controlador.world;

import controlador.MCARenderer;
import modelo.Server;
import modelo.World;
import net.querz.nbt.io.NBTUtil;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.Tag;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

public final class WorldPlayerDataService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private WorldPlayerDataService() {
    }

    public static List<PreviewPlayerData> findRecentPlayers(Server server, World world, int limit) {
        Path worldDir = WorldFilesService.getWorldDirectory(world);
        if (server == null || worldDir == null) {
            return List.of();
        }

        Path playerdataDir = worldDir.resolve("playerdata");
        if (!Files.isDirectory(playerdataDir)) {
            return List.of();
        }

        Map<UUID, String> namesByUuid = loadPlayerNames(server);
        try (Stream<Path> stream = Files.list(playerdataDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName() != null)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".dat"))
                    .map(path -> readRecentPlayer(path, namesByUuid))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(PreviewPlayerData::lastSeen, Comparator.reverseOrder()))
                    .limit(Math.max(1, limit))
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    public static List<PreviewPlayerPoint> findRecentPlayerPoints(Server server, World world, int limit) {
        return findRecentPlayers(server, world, limit).stream()
                .map(player -> new PreviewPlayerPoint(player.username(), player.point()))
                .toList();
    }

    private static PreviewPlayerData readRecentPlayer(Path playerFile, Map<UUID, String> namesByUuid) {
        try {
            String fileName = playerFile.getFileName().toString();
            String uuidText = fileName.substring(0, fileName.length() - 4);
            UUID uuid = UUID.fromString(uuidText);
            String username = namesByUuid.getOrDefault(uuid, uuidText);
            FileTime lastModified = Files.getLastModifiedTime(playerFile);
            MCARenderer.WorldPoint point = readPlayerPosition(playerFile);
            if (point == null) {
                return null;
            }
            return new PreviewPlayerData(username, point, lastModified);
        } catch (Exception ex) {
            return null;
        }
    }

    private static MCARenderer.WorldPoint readPlayerPosition(Path playerFile) {
        try {
            NamedTag namedTag = NBTUtil.read(playerFile.toFile());
            if (namedTag == null || !(namedTag.getTag() instanceof CompoundTag root)) {
                return null;
            }

            ListTag<?> position = root.getListTag("Pos");
            if (position == null || position.size() < 3) {
                return null;
            }

            Double x = readNumber(position.get(0));
            Double z = readNumber(position.get(2));
            if (x == null || z == null) {
                return null;
            }

            return new MCARenderer.WorldPoint((int) Math.round(x), (int) Math.round(z));
        } catch (Exception ex) {
            return null;
        }
    }

    private static Double readNumber(Tag<?> tag) {
        if (tag instanceof net.querz.nbt.tag.NumberTag<?> numberTag) {
            return numberTag.asDouble();
        }
        return null;
    }

    private static Map<UUID, String> loadPlayerNames(Server server) {
        if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return Map.of();
        }

        Path usercachePath = Path.of(server.getServerDir()).resolve("usercache.json");
        if (!Files.isRegularFile(usercachePath)) {
            return Map.of();
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(usercachePath.toFile());
            if (root == null || !root.isArray()) {
                return Map.of();
            }

            Map<UUID, String> names = new HashMap<>();
            for (JsonNode node : root) {
                if (node == null) {
                    continue;
                }
                String uuidText = node.path("uuid").asString(null);
                String name = node.path("name").asString(null);
                if (uuidText == null || name == null || name.isBlank()) {
                    continue;
                }
                try {
                    names.put(UUID.fromString(uuidText), name.strip());
                } catch (IllegalArgumentException ignored) {
                }
            }
            return names;
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
