package controlador.world;

import controlador.Utilidades;
import modelo.Server;
import modelo.World;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class WorldFilesService {
    public static final String WORLD_METADATA_FILE = ".emw-world.properties";
    public static final String PREVIEW_METADATA_FILE = ".preview-overlay.properties";

    private WorldFilesService() {
    }

    public static Properties readWorldMetadata(World world) {
        Properties metadata = new Properties();
        Path metadataPath = getWorldMetadataPath(world);
        if (metadataPath == null || !Files.isRegularFile(metadataPath)) {
            return metadata;
        }

        try (InputStream in = Files.newInputStream(metadataPath)) {
            metadata.load(in);
        } catch (IOException ignored) {
        }
        return metadata;
    }

    public static void writeWorldMetadata(World world, Properties metadata) throws IOException {
        Path metadataPath = getWorldMetadataPath(world);
        if (metadataPath == null) {
            throw new IOException("La ruta de metadata del mundo no es valida.");
        }
        if (metadataPath.getParent() != null) {
            Files.createDirectories(metadataPath.getParent());
        }
        try (OutputStream out = Files.newOutputStream(metadataPath)) {
            metadata.store(out, "Easy MC Server managed world metadata");
        }
    }

    public static Properties readServerProperties(Server server) {
        Properties properties = new Properties();
        Path propertiesPath = getServerPropertiesPath(server);
        if (propertiesPath == null || !Files.isRegularFile(propertiesPath)) {
            return properties;
        }

        try {
            properties.putAll(Utilidades.cargarPropertiesUtf8(propertiesPath));
        } catch (IOException ignored) {
        }
        return properties;
    }

    public static void writeServerProperties(Server server, Properties properties) throws IOException {
        Path propertiesPath = getServerPropertiesPath(server);
        if (propertiesPath == null) {
            throw new IOException("La ruta de server.properties no es valida.");
        }
        if (propertiesPath.getParent() != null) {
            Files.createDirectories(propertiesPath.getParent());
        }
        Utilidades.guardarPropertiesUtf8(propertiesPath, properties, null);
    }

    public static Path getPreviewPath(World world) {
        Path worldDir = getWorldDirectory(world);
        return worldDir == null ? null : worldDir.resolve("preview.png");
    }

    public static Path getPreviewOverlayMetadataPath(World world) {
        Path worldDir = getWorldDirectory(world);
        return worldDir == null ? null : worldDir.resolve(PREVIEW_METADATA_FILE);
    }

    public static Path getWorldMetadataPath(World world) {
        Path worldDir = getWorldDirectory(world);
        return worldDir == null ? null : worldDir.resolve(WORLD_METADATA_FILE);
    }

    public static Path getServerPropertiesPath(Server server) {
        if (server == null || server.getServerDir() == null || server.getServerDir().isBlank()) {
            return null;
        }
        return Path.of(server.getServerDir()).resolve("server.properties");
    }

    public static Path getWorldDirectory(World world) {
        if (world == null || world.getWorldDir() == null || world.getWorldDir().isBlank()) {
            return null;
        }
        return Path.of(world.getWorldDir());
    }
}
