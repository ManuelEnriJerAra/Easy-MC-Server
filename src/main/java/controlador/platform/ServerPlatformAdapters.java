package controlador.platform;

import modelo.extensions.ServerPlatform;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class ServerPlatformAdapters {
    private static final List<ServerPlatformAdapter> ADAPTERS = List.of(
            new PaperServerPlatformAdapter(),
            new ForgeServerPlatformAdapter(),
            new VanillaServerPlatformAdapter()
    );

    private ServerPlatformAdapters() {
    }

    public static List<ServerPlatformAdapter> all() {
        return ADAPTERS;
    }

    public static List<ServerPlatformAdapter> creatable() {
        return ADAPTERS.stream()
                .filter(ServerPlatformAdapter::supportsAutomatedCreation)
                .toList();
    }

    public static ServerPlatformAdapter forPlatform(ServerPlatform platform) {
        ServerPlatform resolved = platform == null ? ServerPlatform.UNKNOWN : platform;
        return ADAPTERS.stream()
                .filter(adapter -> adapter.getPlatform() == resolved)
                .findFirst()
                .orElseGet(VanillaServerPlatformAdapter::new);
    }

    public static ServerPlatformProfile detect(Path serverDir) {
        return ADAPTERS.stream()
                .sorted(Comparator.comparingInt(ServerPlatformAdapter::getDetectionPriority).reversed())
                .map(adapter -> adapter.detect(serverDir))
                .filter(profile -> profile != null)
                .findFirst()
                .orElse(null);
    }
}
