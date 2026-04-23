package controlador.console.vanilla;

import java.nio.file.Path;

/**
 * Describe un runtime de Java local utilizable para generar el catalogo de comandos.
 */
record InstalledJavaRuntime(
        Path executable,
        int majorVersion
) {
}
