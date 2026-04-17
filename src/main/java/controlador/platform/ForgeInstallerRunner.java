package controlador.platform;

import java.io.IOException;
import java.nio.file.Path;

@FunctionalInterface
public interface ForgeInstallerRunner {
    void installServer(Path installerJar, Path targetDirectory) throws IOException;
}
