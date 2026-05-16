package controlador;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

public final class FileSystemSafety {
    private FileSystemSafety() {
    }

    public static boolean isSafeRelativePath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String normalized = path.trim().replace('\\', '/');
        if (normalized.startsWith("/") || normalized.startsWith("\\") || normalized.contains(":")) {
            return false;
        }
        for (String part : normalized.split("/")) {
            if ("..".equals(part) || part.isBlank()) {
                return false;
            }
        }
        try {
            Path candidate = Path.of(normalized).normalize();
            if (candidate.isAbsolute()) {
                return false;
            }
            for (Path part : candidate) {
                if ("..".equals(part.toString())) {
                    return false;
                }
            }
            return !candidate.toString().isBlank();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public static boolean isContained(Path root, Path candidate) {
        if (root == null || candidate == null) {
            return false;
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        return normalizedCandidate.startsWith(normalizedRoot);
    }

    public static Path resolveContainedRelativePath(Path root, String relativePath) throws IOException {
        if (root == null) {
            throw new IOException("No se ha indicado la carpeta base.");
        }
        if (!isSafeRelativePath(relativePath)) {
            throw new IOException("La ruta relativa no es segura: " + relativePath);
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path target = normalizedRoot.resolve(relativePath.replace('\\', '/')).normalize();
        if (!target.startsWith(normalizedRoot)) {
            throw new IOException("La ruta resultante queda fuera de la carpeta base: " + relativePath);
        }
        return target;
    }

    public static boolean isRegularFileNoFollow(Path path) {
        return path != null && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    public static boolean isDirectoryNoFollow(Path path) {
        return path != null && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
    }

    public static void copyDirectoryTree(Path source, Path target) throws IOException {
        if (source == null || target == null) {
            throw new IllegalArgumentException("Las rutas no pueden ser nulas.");
        }
        if (!isDirectoryNoFollow(source)) {
            throw new IllegalArgumentException("El origen no es un directorio: " + source);
        }

        try (Stream<Path> walk = Files.walk(source)) {
            for (Path current : walk.toList()) {
                Path relative = source.relativize(current);
                Path destination = target.resolve(relative);
                if (isDirectoryNoFollow(current)) {
                    Files.createDirectories(destination);
                } else if (isRegularFileNoFollow(current)) {
                    if (destination.getParent() != null) {
                        Files.createDirectories(destination.getParent());
                    }
                    Files.copy(current, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    public static void deleteDirectoryTree(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }

        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        try (Stream<Path> walk = Files.walk(directory)) {
            for (Path path : walk
                    .sorted(Comparator.reverseOrder())
                    .toList()) {
                if (path.toAbsolutePath().normalize().startsWith(normalizedDirectory)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
