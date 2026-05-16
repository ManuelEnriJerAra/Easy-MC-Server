package controlador;

import modelo.extensions.ServerPlatform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

final class ConversionPreservationHelper {
    private static final List<String> ARCHIVOS_CONFIG_PRESERVABLES = List.of(
            "server.properties",
            "eula.txt",
            "ops.json",
            "whitelist.json",
            "banned-players.json",
            "banned-ips.json",
            "permissions.yml",
            "paper-global.yml",
            "paper-world-defaults.yml",
            "spigot.yml",
            "bukkit.yml",
            "server-icon.png"
    );
    private static final Set<String> DIRECTORIOS_EXTENSION_PRESERVABLES = Set.of("mods", "plugins", "config");
    private static final DateTimeFormatter BACKUP_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    record Snapshot(Path source, boolean temporary, Properties preservedProperties) {
    }

    private ConversionPreservationHelper() {
    }

    static Snapshot prepare(Path serverDir, ServerPlatform targetPlatform, boolean createBackup) throws IOException {
        Path preservationSource = createBackup
                ? crearBackupConversion(serverDir, targetPlatform)
                : crearSnapshotPreservacionConversion(serverDir);
        return new Snapshot(
                preservationSource,
                !createBackup,
                cargarPropertiesSilenciosamente(serverDir.resolve("server.properties"))
        );
    }

    static void restore(Snapshot snapshot, Path targetDir) throws IOException {
        if (snapshot == null || targetDir == null) return;
        restaurarDatosTrasConversion(snapshot.source(), targetDir, snapshot.preservedProperties());
    }

    static void cleanup(Snapshot snapshot) {
        if (snapshot == null || !snapshot.temporary() || snapshot.source() == null) {
            return;
        }
        try {
            Utilidades.eliminarDirectorio(snapshot.source());
        } catch (IOException ignored) {
        }
    }

    private static Path crearBackupConversion(Path serverDir, ServerPlatform targetPlatform) throws IOException {
        Path parent = serverDir.getParent();
        if (parent == null) {
            parent = serverDir.toAbsolutePath().getParent();
        }
        if (parent == null) {
            throw new IOException("No se ha podido resolver una carpeta para el backup.");
        }

        String platformName = targetPlatform == null ? "conversion" : targetPlatform.getLegacyTypeName().toLowerCase();
        String backupName = serverDir.getFileName() + "_backup_before_" + platformName + "_" + BACKUP_TIMESTAMP_FORMAT.format(LocalDateTime.now());
        Path backupDir = parent.resolve(backupName);
        Utilidades.copiarDirectorio(serverDir, backupDir);
        return backupDir;
    }

    private static Path crearSnapshotPreservacionConversion(Path serverDir) throws IOException {
        Path snapshotDir = Files.createTempDirectory("dora-conversion-preserve-");
        for (String fileName : ARCHIVOS_CONFIG_PRESERVABLES) {
            Path source = serverDir.resolve(fileName);
            if (Files.isRegularFile(source)) {
                Files.copy(source, snapshotDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        try (Stream<Path> children = Files.list(serverDir)) {
            for (Path child : children.toList()) {
                if (!Files.isDirectory(child)) continue;
                String name = child.getFileName().toString();
                if (!debePreservarseDirectorioEnConversion(child, name)) continue;
                Utilidades.copiarDirectorio(child, snapshotDir.resolve(name));
            }
        }
        return snapshotDir;
    }

    private static void restaurarDatosTrasConversion(Path backupDir, Path targetDir, Properties preservedProperties) throws IOException {
        if (backupDir == null || targetDir == null) return;

        for (String fileName : ARCHIVOS_CONFIG_PRESERVABLES) {
            Path backupFile = backupDir.resolve(fileName);
            Path targetFile = targetDir.resolve(fileName);
            if (Files.isRegularFile(backupFile)
                    && (!Files.exists(targetFile) || debeSobrescribirseArchivoPreservado(fileName))) {
                Files.copy(backupFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        if (preservedProperties != null && !preservedProperties.isEmpty()) {
            Properties current = cargarPropertiesSilenciosamente(targetDir.resolve("server.properties"));
            current.putAll(preservedProperties);
            Utilidades.guardarPropertiesUtf8(targetDir.resolve("server.properties"), current, null);
        }

        try (Stream<Path> children = Files.list(backupDir)) {
            for (Path backupChild : children.toList()) {
                if (!Files.isDirectory(backupChild)) continue;
                String name = backupChild.getFileName().toString();
                if (!debePreservarseDirectorioEnConversion(backupChild, name)) continue;
                Path targetChild = targetDir.resolve(name);
                if (!Files.exists(targetChild)) {
                    Utilidades.copiarDirectorio(backupChild, targetChild);
                }
            }
        }
    }

    private static boolean debePreservarseDirectorioEnConversion(Path backupChild, String name) {
        if (name == null || name.isBlank()) return false;
        if (DIRECTORIOS_EXTENSION_PRESERVABLES.contains(name)) return true;
        if (GestorMundos.DIRECTORIO_MUNDOS.equals(name)) return true;
        if (Files.isRegularFile(backupChild.resolve("level.dat"))) return true;
        return name.endsWith("_nether") || name.endsWith("_the_end");
    }

    private static boolean debeSobrescribirseArchivoPreservado(String fileName) {
        return "server-icon.png".equals(fileName);
    }

    private static Properties cargarPropertiesSilenciosamente(Path propertiesPath) {
        try {
            return Utilidades.cargarPropertiesUtf8(propertiesPath);
        } catch (IOException e) {
            return new Properties();
        }
    }
}
