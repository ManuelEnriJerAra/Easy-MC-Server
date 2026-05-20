package controlador.platform;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@FunctionalInterface
interface ExternalServerInstallerRunner {
    void installServer(Path installerJar, Path targetDirectory, List<String> installerArguments) throws IOException;
}

final class JavaProcessServerInstallerRunner implements ExternalServerInstallerRunner {
    @Override
    public void installServer(Path installerJar, Path targetDirectory, List<String> installerArguments) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-jar");
        command.add(installerJar.toString());
        if (installerArguments != null) {
            command.addAll(installerArguments);
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command);
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
                throw new IOException("El instalador ha finalizado con código " + exitCode + ".\n" + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("La instalación fue interrumpida.", e);
        }
    }
}
