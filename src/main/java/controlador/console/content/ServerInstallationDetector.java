package controlador.console.content;

import modelo.Server;

/**
 * Contrato para detectar el contenido instalado de un servidor.
 */
public interface ServerInstallationDetector {

    ServerInstallationReport detect(Server server);
}
