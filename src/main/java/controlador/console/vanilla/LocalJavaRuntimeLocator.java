package controlador.console.vanilla;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Localiza un runtime de Java compatible con la version objetivo del servidor.
 */
final class LocalJavaRuntimeLocator {
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)(?:\\.\\d+)?");

    Optional<InstalledJavaRuntime> findCompatibleRuntime(int requiredMajorVersion) {
        return discoverCandidates().stream()
                .filter(candidate -> candidate.majorVersion() >= requiredMajorVersion)
                .max(Comparator.comparingInt(InstalledJavaRuntime::majorVersion));
    }

    private List<InstalledJavaRuntime> discoverCandidates() {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();

        Path javaHomeCandidate = executableFromJavaHome(System.getProperty("java.home"));
        if (javaHomeCandidate != null) {
            candidates.add(javaHomeCandidate);
        }

        Path envJavaHomeCandidate = executableFromJavaHome(System.getenv("JAVA_HOME"));
        if (envJavaHomeCandidate != null) {
            candidates.add(envJavaHomeCandidate);
        }

        candidates.addAll(resolveFromSystemPath());

        List<InstalledJavaRuntime> runtimes = new ArrayList<>();
        for (Path candidate : candidates) {
            if (candidate == null || !Files.isRegularFile(candidate)) {
                continue;
            }
            int majorVersion = readMajorVersion(candidate);
            if (majorVersion > 0) {
                runtimes.add(new InstalledJavaRuntime(candidate, majorVersion));
            }
        }
        return List.copyOf(runtimes);
    }

    private Set<Path> resolveFromSystemPath() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> command = os.contains("win")
                ? List.of("where", "java")
                : List.of("which", "-a", "java");

        LinkedHashSet<Path> results = new LinkedHashSet<>();
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isBlank()) {
                        results.add(Path.of(trimmed));
                    }
                }
            }
            process.waitFor();
        } catch (Exception ignored) {
        }
        return Set.copyOf(results);
    }

    private Path executableFromJavaHome(String javaHome) {
        if (javaHome == null || javaHome.isBlank()) {
            return null;
        }
        String executableName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "java.exe"
                : "java";
        Path candidate = Path.of(javaHome).resolve("bin").resolve(executableName);
        return Files.isRegularFile(candidate) ? candidate : null;
    }

    private int readMajorVersion(Path executable) {
        try {
            Process process = new ProcessBuilder(executable.toString(), "-version")
                    .redirectErrorStream(true)
                    .start();
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().findFirst().orElse("");
            }
            process.waitFor();

            Matcher matcher = VERSION_PATTERN.matcher(output);
            if (!matcher.find()) {
                return -1;
            }
            int value = Integer.parseInt(matcher.group(1));
            if (value == 1) {
                Matcher legacy = Pattern.compile("1\\.(\\d+)").matcher(output);
                if (legacy.find()) {
                    return Integer.parseInt(legacy.group(1));
                }
            }
            return value;
        } catch (Exception ex) {
            return -1;
        }
    }
}
