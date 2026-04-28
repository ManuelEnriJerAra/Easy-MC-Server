package controlador.platform;

import modelo.Server;
import modelo.extensions.ServerCapability;
import modelo.extensions.ServerEcosystemType;
import modelo.extensions.ServerLoader;
import modelo.extensions.ServerPlatform;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public interface ServerPlatformAdapter {
    ServerPlatform getPlatform();

    default int getDetectionPriority() {
        return 0;
    }

    ServerPlatformProfile detect(Path serverDir);

    ServerValidationResult validate(Path serverDir);

    void install(Server server, ServerInstallationRequest request) throws IOException;

    ProcessBuilder buildStartProcess(Server server, Path executableJar);

    Path resolveExecutableJar(Path serverDir) throws IOException;

    default boolean requiresExecutableJarForStart() {
        return true;
    }

    default boolean supportsAutomatedCreation() {
        return false;
    }

    default String getCreationDisplayName() {
        return getPlatform().getLegacyTypeName();
    }

    default List<ServerCreationOption> listCreationOptions() throws IOException {
        return List.of();
    }

    default ServerLoader getLoader() {
        return getPlatform().getDefaultLoader();
    }

    default ServerEcosystemType getEcosystemType() {
        return getPlatform().getDefaultEcosystemType();
    }

    default Set<ServerCapability> getCapabilities() {
        return getPlatform().defaultCapabilities();
    }

    default List<Path> getExtensionDirectories(Path serverDir) {
        return List.of();
    }
}
