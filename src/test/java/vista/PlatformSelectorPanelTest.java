package vista;

import controlador.platform.ServerInstallationRequest;
import controlador.platform.ServerPlatformAdapter;
import controlador.platform.ServerPlatformProfile;
import controlador.platform.ServerValidationResult;
import modelo.Server;
import modelo.extensions.ServerPlatform;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformSelectorPanelTest {
    @Test
    void disabledInitialSelectionIsIgnoredAndEnabledSelectionWorks() {
        ServerPlatformAdapter disabled = adapter(ServerPlatform.FORGE);
        ServerPlatformAdapter enabled = adapter(ServerPlatform.FABRIC);
        PlatformSelectorPanel panel = new PlatformSelectorPanel(
                List.of(disabled, enabled),
                disabled,
                null,
                Map.of(ServerPlatform.FORGE, "No disponible")
        );

        assertThat(panel.getSelectedAdapter()).isNull();

        panel.setSelectedAdapter(enabled);

        assertThat(panel.getSelectedAdapter()).isEqualTo(enabled);
    }

    private static ServerPlatformAdapter adapter(ServerPlatform platform) {
        return new ServerPlatformAdapter() {
            @Override
            public ServerPlatform getPlatform() {
                return platform;
            }

            @Override
            public ServerPlatformProfile detect(Path serverDir) {
                return null;
            }

            @Override
            public ServerValidationResult validate(Path serverDir) {
                return ServerValidationResult.ok();
            }

            @Override
            public void install(Server server, ServerInstallationRequest request) throws IOException {
            }

            @Override
            public ProcessBuilder buildStartProcess(Server server, Path executableJar) {
                return new ProcessBuilder("java", "-jar", executableJar.toString());
            }

            @Override
            public Path resolveExecutableJar(Path serverDir) {
                return serverDir.resolve("server.jar");
            }
        };
    }
}
