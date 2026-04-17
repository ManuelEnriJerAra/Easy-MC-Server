package controlador.platform;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class JavaProcessForgeInstallerRunner implements ForgeInstallerRunner {
    @Override
    public void installServer(Path installerJar, Path targetDirectory) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "java",
                "-jar",
                installerJar.toString(),
                "--installServer"
        );
        processBuilder.directory(targetDirectory.toFile());
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("El instalador de Forge ha finalizado con codigo " + exitCode + ".\n" + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("La instalacion de Forge fue interrumpida.", e);
        }
    }
}
