package support;

import controlador.console.ConsoleCommandContext;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public final class ConsoleSuggestionFixtures {
    private ConsoleSuggestionFixtures() {
    }

    public static ConsoleCommandContext vanilla1211() {
        return new ConsoleCommandContext(
                "server-vanilla-1211",
                "Vanilla 1.21.1",
                "C:/servers/vanilla-1211",
                "1.21.1",
                "VANILLA",
                true,
                Set.of("Steve", "Alex"),
                Set.of("Steve", "Alex", "Builder"),
                Set.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
                Set.of("#minecraft:skeletons"),
                Set.of(),
                Set.of(),
                "fingerprint-vanilla-1211",
                Instant.now(),
                Map.of(
                        "ops", Set.of("Steve"),
                        "whitelist", Set.of("Steve", "Alex"),
                        "bannedPlayers", Set.of(),
                        "bannedIps", Set.of(),
                        "liveCommands", Set.of()
                ),
                Map.of()
        );
    }

    public static ConsoleCommandContext vanilla1204() {
        return new ConsoleCommandContext(
                "server-vanilla-1204",
                "Vanilla 1.20.4",
                "C:/servers/vanilla-1204",
                "1.20.4",
                "VANILLA",
                true,
                Set.of("Steve"),
                Set.of("Steve", "Alex"),
                Set.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
                Set.of("#minecraft:raiders"),
                Set.of(),
                Set.of(),
                "fingerprint-vanilla-1204",
                Instant.now(),
                Map.of(
                        "ops", Set.of("Steve"),
                        "whitelist", Set.of("Steve", "Alex"),
                        "bannedPlayers", Set.of(),
                        "bannedIps", Set.of(),
                        "liveCommands", Set.of()
                ),
                Map.of()
        );
    }

    public static ConsoleCommandContext fabricModded() {
        return new ConsoleCommandContext(
                "server-fabric",
                "Fabric Modded",
                "C:/servers/fabric",
                "1.21.1",
                "FABRIC",
                true,
                Set.of("ModAdmin"),
                Set.of("ModAdmin", "Alex"),
                Set.of("minecraft:overworld"),
                Set.of("#kubejs:custom"),
                Set.of("create", "kubejs"),
                Set.of(),
                "fingerprint-fabric",
                Instant.now(),
                Map.of(
                        "ops", Set.of("ModAdmin"),
                        "whitelist", Set.of("ModAdmin", "Alex"),
                        "bannedPlayers", Set.of(),
                        "bannedIps", Set.of(),
                        "liveCommands", Set.of()
                ),
                Map.of("brigadierExportAvailable", "false")
        );
    }

    public static ConsoleCommandContext paperPluginServer() {
        return new ConsoleCommandContext(
                "server-paper",
                "Paper Plugins",
                "C:/servers/paper",
                "1.21.1",
                "PAPER",
                true,
                Set.of("Admin", "Moderator"),
                Set.of("Admin", "Moderator", "Alex"),
                Set.of("minecraft:overworld"),
                Set.of(),
                Set.of(),
                Set.of("EssentialsX", "LuckPerms"),
                "fingerprint-paper",
                Instant.now(),
                Map.of(
                        "ops", Set.of("Admin"),
                        "whitelist", Set.of("Admin", "Moderator"),
                        "bannedPlayers", Set.of(),
                        "bannedIps", Set.of(),
                        "liveCommands", Set.of()
                ),
                Map.of()
        );
    }
}
